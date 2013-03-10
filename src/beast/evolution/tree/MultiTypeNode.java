/*
 * Copyright (C) 2013 Tim Vaughan <tgvaughan@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package beast.evolution.tree;

import beast.core.Description;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("A node in a multi-type phylogenetic tree.")
public class MultiTypeNode extends Node {
    
    int nTypeChanges = 0;
    List<Integer> changeTypes = new ArrayList<Integer>();
    List<Double> changeTimes = new ArrayList<Double>();
    int nodeType = 0;
    
    List<MultiTypeNode> multiTypeChildren = new ArrayList<MultiTypeNode>();
    MultiTypeNode multiTypeParent = null;
    
    /**
     * Retrieve the total number of changes on the branch above this node.
     * @return type change count
     */
    public int getChangeCount() {
        return nTypeChanges;
    }
    
    /**
     * Obtain destination type (reverse time) of the change specified by idx
     * on the branch between this node and its parent.
     * 
     * @param idx
     * @return change type
     */
    public int getChangeType(int idx) {
        return changeTypes.get(idx);
    }
    
    /**
     * Obtain time of the change specified by idx on the branch between
     * this node and its parent.
     * 
     * @param idx
     * @return time of change
     */
    public double getChangeTime(int idx) {
        return changeTimes.get(idx);
    }
    
    /**
     * Retrieve final type on branch above this node.
     * 
     * @return final type
     */
    public int getFinalType() {
        if (nTypeChanges>0)
            return changeTypes.get(nTypeChanges-1);
        else
            return nodeType;
    }
    
    /**
     * Retrieve time of final type change on branch above this node,
     * or the height of the node if no changes exist.
     * 
     * @return final type change time
     */
    public double getFinalChangeTime() {
        if (nTypeChanges>0)
            return changeTimes.get(nTypeChanges-1);
        else
            return getHeight();
    }

    /**
     * Obtain type value at this node.  (Used for caching or for specifying
     * type changes at nodes.)
     * 
     * @return type value at this node
     */
    public int getNodeType() {
        return nodeType;
    }
    
    /**
     * Sets type of node.
     * @param nodeType New node type.
     */
    public void setNodeType(int nodeType) {
        startEditing();
        this.nodeType = nodeType;
    }

    /**
     * Add a new type change to branch above node.
     * 
     * @param newType Destination (reverse time) type of change
     * @param time Time at which change occurs.
     */
    public void addChange(int newType, double time) {
        changeTypes.add(newType);
        changeTimes.add(time);
        nTypeChanges += 1;
    }
    
    /***************************
     * Method ported from Node *
     ***************************/
    
    @Override
    public MultiTypeNode getParent() {
        return multiTypeParent;
    }
    
    /**
     * Return list of children of this multi-type tree node.
     * @return 
     */
    public List<MultiTypeNode> getMultiTypeChildren() {
        return multiTypeChildren;
    }
    
    @Override
    public List<Node> getChildren() {
        return new ArrayList<Node>(multiTypeChildren);
    }
    
    @Override
    public MultiTypeNode getLeft() {
		if (multiTypeChildren.isEmpty()) {
			return null;
		}
		return multiTypeChildren.get(0);
	}
    
    @Override
    public MultiTypeNode getRight() {
		if (multiTypeChildren.size() <= 1) {
			return null;
		}
		return multiTypeChildren.get(1);
	}
    
    @Override
    public boolean isLeaf() {
        return multiTypeChildren.isEmpty();
    }
    
    @Override
    public boolean isRoot() {
        return multiTypeParent == null;
    }
    
    /**
     * get all child node under this node, if this node is leaf then list.size() = 0.
     *
     * @return
     */
    public List<MultiTypeNode> getAllMultiTypeChildNodes() {
        List<MultiTypeNode> childNodes = new ArrayList<MultiTypeNode>();
        if (!this.isLeaf()) getAllMultiTypeChildNodes(childNodes);
        return childNodes;
    }
    
    @Override
    public List<Node> getAllChildNodes() {
        return new ArrayList<Node>(getAllMultiTypeChildNodes());
    }

    // recursive
    public void getAllMultiTypeChildNodes(List<MultiTypeNode> childNodes) {
        childNodes.add(this);
        if (!this.isLeaf()) {
            getRight().getAllMultiTypeChildNodes(childNodes);
            getLeft().getAllMultiTypeChildNodes(childNodes);
        }
    }

    /**
     * get all leaf node under this node, if this node is leaf then list.size() = 0.
     *
     * @return
     */
    public List<MultiTypeNode> getAllMultiTypeLeafNodes() {
        List<MultiTypeNode> leafNodes = new ArrayList<MultiTypeNode>();
        if (!this.isLeaf()) getAllMultiTypeLeafNodes(leafNodes);
        return leafNodes;
    }
    
    @Override
    public List<Node> getAllLeafNodes() {
        return new ArrayList<Node>(getAllMultiTypeLeafNodes());
    }

    // recursive
    public void getAllMultiTypeLeafNodes(List<MultiTypeNode> leafNodes) {
        if (this.isLeaf()) {
            leafNodes.add(this);
        } else {
            getRight().getAllMultiTypeLeafNodes(leafNodes);
            getLeft().getAllMultiTypeLeafNodes(leafNodes);
        }
    }
    
    /**
     * Let tree know that it's been modified.
     */
    private void startEditing() {
        if (m_tree != null && m_tree.getState() != null) {
            m_tree.startEditing(null);
        }
    }
    
    /**
     * Set a new parent for this multi-type node.
     * @param newParent 
     */
    public void setParent(MultiTypeNode newParent) {
        startEditing();
        multiTypeParent = newParent;
        m_Parent = newParent;
    }
    
    
    /**
     * @return (deep) copy of node
     */
    @Override
    public MultiTypeNode copy() {
        MultiTypeNode node = new MultiTypeNode();
        node.m_fHeight = m_fHeight;
        node.m_iLabel = m_iLabel;
        node.m_sMetaData = m_sMetaData;
        node.m_Parent = null;
        node.m_sID = m_sID;
        node.nTypeChanges = nTypeChanges;
        node.changeTimes.addAll(changeTimes);
        node.changeTypes.addAll(changeTypes);
        if (getLeft() != null) {
            node.setLeft(getLeft().copy());
            node.getLeft().m_Parent = node;
            if (getRight() != null) {
                node.setRight(getRight().copy());
                node.getRight().m_Parent = node;
            }
        }
        return node;
    }
    
}

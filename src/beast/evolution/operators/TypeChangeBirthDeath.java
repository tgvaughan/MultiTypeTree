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
package beast.evolution.operators;

import beast.core.Description;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.Node;
import beast.util.Randomizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Migration birth/death operator from Ewing et al., 2004.")
public class TypeChangeBirthDeath extends MultiTypeTreeOperator {

    
    private Set<Integer> illegalTypes;
    
    @Override
    public void initAndValidate() {
        illegalTypes = new HashSet<Integer>();
    }
    
    @Override
    public double proposal() {
        mtTree = multiTypeTreeInput.get();
        
        // Immediate reject if <3 types in model
        if (mtTree.getNTypes()<3)
            return Double.NEGATIVE_INFINITY;
        
        // Select event at random:
        int event = Randomizer.nextInt(mtTree.getTotalNumberOfChanges()
                + mtTree.getNodeCount()-1);
        
        MultiTypeNode node = null;
        int changeIdx = -1;
        if (event < mtTree.getNodeCount()-1) {            
            node = (MultiTypeNode)mtTree.getNode(event);
            
            // Stop-gap in case Remco discards the "last node is root" convention:
            if (node.isRoot())
                node = (MultiTypeNode)mtTree.getNode(event+1);
            
        } else {
            event -= mtTree.getNodeCount()-1;
            for (Node thisNode : mtTree.getNodesAsArray()) {
                if (thisNode.isRoot())
                    continue;
                
                if (event < ((MultiTypeNode)thisNode).getChangeCount()) {
                    node = (MultiTypeNode)thisNode;
                    changeIdx = event;
                    break;
                }
                
                event -= ((MultiTypeNode)thisNode).getChangeCount();
            }
        }
        
        if (Randomizer.nextBoolean())
            return birthMove(node, changeIdx);
        else
            return deathMove(node, changeIdx);
        
    }
    
    private double birthMove(MultiTypeNode node, int changeIdx) {
        double logHR = 0.0;
        
        // Construct set of illegal change types:
        illegalTypes.clear();
        
        if (changeIdx<0)
            illegalTypes.add(node.getNodeType());
        else
            illegalTypes.add(node.getChangeType(changeIdx));

        if (changeIdx+1<node.getChangeCount())
            illegalTypes.add(node.getChangeType(changeIdx+1));
        else {
            try {
                getIllegalTypes(illegalTypes, (MultiTypeNode)node.getParent(), node);
            } catch (Exception ex) {
                // Subtree contains leaf node
                return Double.NEGATIVE_INFINITY;
            }
        }
        
        // No legal moves
        if (illegalTypes.size()==mtTree.getNTypes())
            return Double.NEGATIVE_INFINITY;
       
        // Select legal change type:
        int idx = Randomizer.nextInt(mtTree.getNTypes()- illegalTypes.size());
        int changeType;
        for (changeType=0; changeType<mtTree.getNTypes(); changeType++) {
            if (illegalTypes.contains(changeType))
                continue;
            if (idx==0)
                break;
            idx -= 1;
        }
        
        // 
        
        return logHR;
    }
    
    private double deathMove(MultiTypeNode node, int changeIdx) {
        double logHR = 0.0;
        
        return logHR;
    }
    
    /**
     * Populates illegalTypes set with types of subtree containing node and no intervening migration events.
     * @param node
     * @param prevNode 
     * @return 
     */
    private void getIllegalTypes(Set<Integer> illegalTypes, MultiTypeNode node, MultiTypeNode prevNode) throws Exception {

        if (node.isLeaf())
            throw new Exception("Leaf in sub-tree.");
        
        if (prevNode == node.getParent()) {
            
            MultiTypeNode left = (MultiTypeNode)node.getLeft();
            MultiTypeNode right = (MultiTypeNode)node.getRight();
            
            if (left.getChangeCount()>0) {
                if (left.getChangeCount()>1)
                    illegalTypes.add(left.getChangeType(left.getChangeCount()-2));
                else
                    illegalTypes.add(left.getNodeType());
                
            } else
                getIllegalTypes(illegalTypes, left, node);
            
            if (right.getChangeCount()>0)
                if (right.getChangeCount()>1)
                    illegalTypes.add(right.getChangeType(right.getChangeCount()-2));
                else
                    illegalTypes.add(right.getNodeType());
            else
                getIllegalTypes(illegalTypes, right, node);
            
        } else {
            
            MultiTypeNode parent = (MultiTypeNode)node.getParent();
            MultiTypeNode sister = (MultiTypeNode)getOtherChild(parent, node);
            
            if (parent.getChangeCount()>0)
                illegalTypes.add(parent.getChangeType(0));
            else
                getIllegalTypes(illegalTypes, parent, node);


            if (sister.getChangeCount()>0) {
                if (sister.getChangeCount()>1)
                    illegalTypes.add(sister.getChangeType(sister.getChangeCount()-2));
                else
                    illegalTypes.add(sister.getNodeType());
            } else
                getIllegalTypes(illegalTypes, sister, node);
        }
        
    }
    
    /**
     * Assigns a new type to all branches within the subtree attached to node.
     * 
     * @param type
     * @param node
     * @param prevNode 
     */
    private void retypeSubtree(int type, MultiTypeNode node, MultiTypeNode prevNode) {
        if (node.isLeaf())
            throw new IllegalArgumentException("Leaf attached to subtree: cannot retype!");
        
        if (prevNode == node.getParent()) {
            
            MultiTypeNode left = (MultiTypeNode)node.getLeft();
            MultiTypeNode right = (MultiTypeNode)node.getRight();
            
            if (left.getChangeCount()>0)
                left.setChangeType(left.getChangeCount()-1, type);
            else
                retypeSubtree(type, left, node);
            
            if (right.getChangeCount()>0)
                right.setChangeType(right.getChangeCount()-1, type);
            else
                retypeSubtree(type, right, node);
            
        } else {
            
            MultiTypeNode parent = (MultiTypeNode)node.getParent();
            MultiTypeNode sister = (MultiTypeNode)getOtherChild(parent, node);
            
            if (parent.getChangeCount()==0)
                retypeSubtree(type, parent, node);


            if (sister.getChangeCount()>0)
                sister.setChangeType(sister.getChangeCount()-1, type);
            else
                retypeSubtree(type, sister, node);
            
        }
            
    }
    
}

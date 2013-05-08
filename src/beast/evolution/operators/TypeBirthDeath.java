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
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Migration birth/death operator from Ewing et al., 2004.")
public class TypeBirthDeath extends MultiTypeTreeOperator {

    
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
                + mtTree.getInternalNodeCount()-1);
        
        MultiTypeNode node = null;
        int changeIdx = -1;
        if (event < mtTree.getInternalNodeCount()-1) {            
            node = (MultiTypeNode)mtTree.getNode(event + mtTree.getLeafNodeCount());
            
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
        
        // Construct the set of illegal change types for the reverse move:
        illegalTypes.clear();
        
        if (changeIdx<0) {
            MultiTypeNode startNode = findDecendentNodeWithMigration((MultiTypeNode)node.getLeft());
            
            if (startNode.getChangeCount()==0)
                // Termiated at leaf: move would be irreversable
                return Double.NEGATIVE_INFINITY;
            
            if (startNode.getChangeCount()==1)
                illegalTypes.add(startNode.getNodeType());
            else
                illegalTypes.add(startNode.getChangeType(startNode.getChangeCount()-2));
            
            try {
                getIllegalTypes(illegalTypes,
                        (MultiTypeNode)startNode.getParent(), startNode);
            } catch (Exception ex) {
                // Subtree contains leaf node
                return Double.NEGATIVE_INFINITY;
            }
            
        } else {
            if (changeIdx==0) {
                illegalTypes.add(node.getNodeType());
            } else {
                illegalTypes.add(node.getChangeType(changeIdx-1));
            }
            
            if (changeIdx+1<node.getChangeCount()) {
                illegalTypes.add(node.getChangeType(changeIdx+1));
            } else {
                try {
                    getIllegalTypes(illegalTypes, (MultiTypeNode)node.getParent(), node);
                } catch (Exception ex) {
                    // Subtree contains leaf node
                    return Double.NEGATIVE_INFINITY;
                }
            }
        }
        
        // Record number of legal change types in reverse move for HR:
        int Cdeath = mtTree.getNTypes() - illegalTypes.size();
        if (Cdeath == 0)
            // Reverse move is impossible
            return Double.NEGATIVE_INFINITY;
        
        // Reverse move HR contribution:
        logHR += Math.log(1.0/(Cdeath
                *(mtTree.getTotalNumberOfChanges() + mtTree.getInternalNodeCount())));
        
        // Construct set of illegal change types for the forward move:
        illegalTypes.clear();
        
        double tmin, tmax;
        if (changeIdx<0) {
            illegalTypes.add(node.getNodeType());
            tmin = node.getHeight();
        } else {
            illegalTypes.add(node.getChangeType(changeIdx));
            tmin = node.getChangeTime(changeIdx);
        }

        if (changeIdx+1<node.getChangeCount()) {
            illegalTypes.add(node.getChangeType(changeIdx+1));
            tmax = node.getChangeTime(changeIdx+1);
        } else {
            try {
                getIllegalTypes(illegalTypes, (MultiTypeNode)node.getParent(), node);
            } catch (Exception ex) {
                // Subtree contains leaf node
                return Double.NEGATIVE_INFINITY;
            }
            tmax = node.getParent().getHeight();
        }
        
        // Record number of legal change types in forward move for HR:
        int Cbirth = mtTree.getNTypes() - illegalTypes.size();
        
        // No legal moves
        if (Cbirth == 0)
            return Double.NEGATIVE_INFINITY;
       
        // Select legal change type:
        int changeType = selectLegalChangeType();
        
        // Choose new change time:
        double tnew = tmin + (tmax-tmin)*Randomizer.nextDouble();
        
        // Implement new change:
        node.insertChange(changeIdx+1, changeType, tnew);
        
        // Propagate changes across subtree:
        if (changeIdx+2>=node.getChangeCount())
            retypeSubtree(changeType, (MultiTypeNode)node.getParent(), node);
        
        // Forward move HR contribution:
        logHR -= Math.log(1.0/(Cbirth
                *(mtTree.getTotalNumberOfChanges()+mtTree.getInternalNodeCount()-1)
                *(tmax-tmin)));
        
        return logHR;
    }
    
    private double deathMove(MultiTypeNode node, int changeIdx) {
        double logHR = 0.0;
        
        // Reject if event above (node,changeIdx) is not a migration
        if (changeIdx+1>=node.getChangeCount())
            return Double.NEGATIVE_INFINITY;
        
        // Construct set of illegal change types for reverse move:
        illegalTypes.clear();
        
        double tmin;
        if (changeIdx<0) {
            illegalTypes.add(node.getNodeType());
            tmin = node.getHeight();
        } else {
            illegalTypes.add(node.getChangeType(changeIdx));
            tmin = node.getChangeTime(changeIdx);
        }

        double tmax;
        if (changeIdx+2<node.getChangeCount()) {
            illegalTypes.add(node.getChangeType(changeIdx+2));
            tmax = node.getChangeTime(changeIdx+2);
        } else {
            try {
                getIllegalTypes(illegalTypes, (MultiTypeNode)node.getParent(), node);
            } catch (Exception ex) {
                // Subtree contains a leaf
                return Double.NEGATIVE_INFINITY;
            }
            tmax = node.getParent().getHeight();
        }
        
        // Record number of legal change types for reverse move HR
        int Cbirth = mtTree.getNTypes() - illegalTypes.size();
        if (Cbirth==0)
            // Reverse move impossible
            return Double.NEGATIVE_INFINITY;
        
        // Reverse move HR contribution
        logHR += Math.log(1.0/(Cbirth
                *(mtTree.getTotalNumberOfChanges()+mtTree.getInternalNodeCount()-2)
                *(tmax-tmin)));
        
        // Construct set of illegal change types for forward move:
        illegalTypes.clear();
        
        if (changeIdx<0) {
            MultiTypeNode startNode = findDecendentNodeWithMigration((MultiTypeNode)node.getLeft());
            
            if (startNode.getChangeCount()==0)
                // subtree includes leaf: abort
                return Double.NEGATIVE_INFINITY;
            
            if (startNode.getChangeCount()==1)
                illegalTypes.add(startNode.getNodeType());
            else
                illegalTypes.add(startNode.getChangeType(startNode.getChangeCount()-2));
            
            try {
                getIllegalTypes(illegalTypes, (MultiTypeNode)startNode.getParent(), startNode);
            } catch (Exception ex) {
                // subtree includes leaf: abort
                return Double.NEGATIVE_INFINITY;
            }
            
        } else {
            if (changeIdx==0)
                illegalTypes.add(node.getNodeType());
            else
                illegalTypes.add(node.getChangeType(changeIdx-1));
            
            if (changeIdx+2<node.getChangeCount()) {
                illegalTypes.add(node.getChangeType(changeIdx+2));
            } else {
                try {
                    getIllegalTypes(illegalTypes, (MultiTypeNode)node.getParent(), node);
                } catch (Exception ex) {
                    // subtree includes leaf: abort
                    return Double.NEGATIVE_INFINITY;
                }
            }
        }
        
        // Record number of legal change types in forward move for HR
        int Cdeath = mtTree.getNTypes() - illegalTypes.size();
        
        // Reject if no legal change types:
        if (Cdeath == 0)
            return Double.NEGATIVE_INFINITY;
        
        // Select legal change type:
        int changeType = selectLegalChangeType();
        
        // Remove dying type change:
        node.removeChange(changeIdx+1);
        
        // Implement subtree type changes
        if (changeIdx<0) {
            MultiTypeNode startNode = findDecendentNodeWithMigration((MultiTypeNode)node.getLeft());
            startNode.setChangeType(startNode.getChangeCount()-1, changeType);
            retypeSubtree(changeType, (MultiTypeNode)startNode.getParent(), startNode);
        } else {
            node.setChangeType(changeIdx, changeType);
            if (changeIdx>=node.getChangeCount())
                retypeSubtree(changeType, (MultiTypeNode)node.getParent(), node);
        }
        
        // Forward move HR contribution
        logHR -= Math.log(1.0/(Cdeath*(mtTree.getTotalNumberOfChanges()+mtTree.getInternalNodeCount()-1)));
        
        return logHR;
    }
    
    /**
     * Populates illegalTypes set with types of subtree containing node and no
     * intervening migration events.
     * 
     * @param node
     * @param prevNode 
     * @return 
     */
    private void getIllegalTypes(Set<Integer> illegalTypes,
            MultiTypeNode node, MultiTypeNode prevNode) throws Exception {

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
    
    /**
     * Does what it says.
     * 
     * @param node
     * @return leaf node or node with a migration event on it.
     */
    private MultiTypeNode findDecendentNodeWithMigration(MultiTypeNode node) {
        if (node.isLeaf() || node.getChangeCount()>0)
            return node;
        else
            return findDecendentNodeWithMigration((MultiTypeNode)node.getLeft());
    }
    
    
    /**
     * Choose a change type uniformly at random from the types not found
     * in the this.illegalTypes set.
     * 
     * @return chosen type
     */
    private int selectLegalChangeType() {
        int n = Randomizer.nextInt(mtTree.getNTypes()- illegalTypes.size());
        int changeType;
        for (changeType=0; changeType<mtTree.getNTypes(); changeType++) {
            if (illegalTypes.contains(changeType))
                continue;
            if (n==0)
                break;
            n -= 1;
        }
        
        return n;
    }
}

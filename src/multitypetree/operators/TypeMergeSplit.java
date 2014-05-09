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
package multitypetree.operators;

import beast.core.Description;
import beast.core.Input;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.Node;
import beast.util.Randomizer;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Implements type change (migration) merge/split move as "
        + "described by Ewing et al., Genetics (2004).")
public class TypeMergeSplit extends MultiTypeTreeOperator {
    
    public Input<Boolean> includeRootInput = new Input<Boolean>("includeRoot",
            "Include Tim's root merge/split moves.  Default false.", false);

    @Override
    public void initAndValidate() { }
    
    @Override
    public double proposal() {
        
        mtTree = multiTypeTreeInput.get();
        
        Node node;
        do {
            node = mtTree.getNode(mtTree.getLeafNodeCount()
                    + Randomizer.nextInt(mtTree.getInternalNodeCount()));
        } while (!includeRootInput.get() && node.isRoot());
        
        // Randomly select a merge or split proposal:
        if (Randomizer.nextDouble()<0.5) {
            if (node.isRoot())
                return splitProposalRoot();
            else
                return splitProposal(node);
        } else {
            if (node.isRoot())
                return mergeProposalRoot();
            else
                return mergeProposal(node);
        }
    
    }
    
    /**
     * Remove a migration event above a coalescence event and create two
     * below it.
     * 
     * @param node Node representing coalescence event.
     * @return log of Hastings ratio
     */
    private double splitProposal(Node node) {
        
        if (((MultiTypeNode)node).getChangeCount()==0)
            return Double.NEGATIVE_INFINITY;
        
        int type = ((MultiTypeNode)node).getChangeType(0);
        
        // Delete old change above node:
        ((MultiTypeNode)node).removeChange(0);
        
        // Update node colour:
        ((MultiTypeNode)node).setNodeType(type);

        // Select new change times:
        
        double tminLeft = ((MultiTypeNode)node.getLeft()).getFinalChangeTime();
        double tnewLeft = (node.getHeight()-tminLeft)*Randomizer.nextDouble()
                + tminLeft;
        
        double tminRight = ((MultiTypeNode)node.getRight()).getFinalChangeTime();
        double tnewRight = (node.getHeight()-tminRight)*Randomizer.nextDouble()
                + tminRight;
        
        // Record time of first migration or coalescence above node
        // for HR calculation:
        double tmax;
        if (((MultiTypeNode)node).getChangeCount()>0)
            tmax = ((MultiTypeNode)node).getChangeTime(0);
        else
            tmax = node.getParent().getHeight();

        // Add new changes below node:
        ((MultiTypeNode)node.getLeft()).addChange(type, tnewLeft);
        ((MultiTypeNode)node.getRight()).addChange(type, tnewRight);
        
        return Math.log((node.getHeight()-tminRight)*(node.getHeight()-tminLeft))
                - Math.log(tmax-node.getHeight());
    }
    
    private double splitProposalRoot() {
        
        Node root = mtTree.getRoot();

        // Select new root type:
        int oldType = ((MultiTypeNode)root).getNodeType();
        int type;
        do {
            type = Randomizer.nextInt(mtTree.getNTypes());
        } while (type == oldType);
        
        // Update node type:
        ((MultiTypeNode)root).setNodeType(type);

        // Select new change times:
        
        double tminLeft = ((MultiTypeNode)root.getLeft()).getFinalChangeTime();
        double tnewLeft = (root.getHeight()-tminLeft)*Randomizer.nextDouble()
                + tminLeft;
        
        double tminRight = ((MultiTypeNode)root.getRight()).getFinalChangeTime();
        double tnewRight = (root.getHeight()-tminRight)*Randomizer.nextDouble()
                + tminRight;
       
        // Add new changes below node:
        ((MultiTypeNode)root.getLeft()).addChange(type, tnewLeft);
        ((MultiTypeNode)root.getRight()).addChange(type, tnewRight);
        
        return Math.log((root.getHeight()-tminRight)*(root.getHeight()-tminLeft)
                *(mtTree.getNTypes()-1));

    }
    
    /**
     * Remove a pair of migrations below a coalescence event and create
     * one above it.
     * 
     * @param node Node representing coalescence event.
     * @return log of Hastings ratio
     */
    private double mergeProposal(Node node) {
        
        MultiTypeNode left = (MultiTypeNode)node.getLeft();
        MultiTypeNode right = (MultiTypeNode)node.getRight();
        MultiTypeNode mtNode = (MultiTypeNode)node;
        
        int leftIdx = left.getChangeCount()-1;
        int rightIdx = right.getChangeCount()-1;
        
        if (leftIdx<0 || rightIdx<0)
            return Double.NEGATIVE_INFINITY;

        int leftType = left.getChangeType(leftIdx);
        int rightType = right.getChangeType(rightIdx);
        
        if (leftType != rightType)
            return Double.NEGATIVE_INFINITY;
        
        int leftTypeUnder;
        double tminLeft;
        if (leftIdx>0) {
            leftTypeUnder = left.getChangeType(leftIdx-1);
            tminLeft = left.getChangeTime(leftIdx-1);
        } else {
            leftTypeUnder = left.getNodeType();
            tminLeft = left.getHeight();
        }
        
        int rightTypeUnder;
        double tminRight;
        if (rightIdx>0) {
            rightTypeUnder = right.getChangeType(rightIdx-1);
            tminRight = right.getChangeTime(rightIdx-1);
        } else {
            rightTypeUnder = right.getNodeType();
            tminRight = right.getHeight();
        }

        if (leftTypeUnder != rightTypeUnder)
            return Double.NEGATIVE_INFINITY;
        
        double tmax;
        if (mtNode.getChangeCount()>0)
            tmax = mtNode.getChangeTime(0);
        else
            tmax = node.getParent().getHeight();
        
        left.removeChange(leftIdx);
        right.removeChange(rightIdx);        
        mtNode.setNodeType(leftTypeUnder);
        
        double tnew = Randomizer.nextDouble()*(tmax-node.getHeight())
                + node.getHeight();
        
        mtNode.insertChange(0, leftType, tnew);
        
        return Math.log(tmax-node.getHeight())
                - Math.log((node.getHeight()-tminLeft)*(node.getHeight()-tminRight));
    }
    
    private double mergeProposalRoot() {
        
        MultiTypeNode root = (MultiTypeNode)mtTree.getRoot();
             
        MultiTypeNode left = (MultiTypeNode)root.getLeft();
        MultiTypeNode right = (MultiTypeNode)root.getRight();
        
        int leftIdx = left.getChangeCount()-1;
        int rightIdx = right.getChangeCount()-1;
        
        if (leftIdx<0 || rightIdx<0)
            return Double.NEGATIVE_INFINITY;

        int leftType = left.getChangeType(leftIdx);
        int rightType = right.getChangeType(rightIdx);
        
        if (leftType != rightType)
            return Double.NEGATIVE_INFINITY;
        
        int leftTypeUnder;
        double tminLeft;
        if (leftIdx>0) {
            leftTypeUnder = left.getChangeType(leftIdx-1);
            tminLeft = left.getChangeTime(leftIdx-1);
        } else {
            leftTypeUnder = left.getNodeType();
            tminLeft = left.getHeight();
        }
        
        int rightTypeUnder;
        double tminRight;
        if (rightIdx>0) {
            rightTypeUnder = right.getChangeType(rightIdx-1);
            tminRight = right.getChangeTime(rightIdx-1);
        } else {
            rightTypeUnder = right.getNodeType();
            tminRight = right.getHeight();
        }

        if (leftTypeUnder != rightTypeUnder)
            return Double.NEGATIVE_INFINITY;
        
        
        left.removeChange(leftIdx);
        right.removeChange(rightIdx);        
        root.setNodeType(leftTypeUnder);
        
        return -Math.log((root.getHeight()-tminRight)*(root.getHeight()-tminLeft)
                *(mtTree.getNTypes()-1));
    }
    
}

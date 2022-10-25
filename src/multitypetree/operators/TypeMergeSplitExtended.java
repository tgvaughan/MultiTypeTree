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

import beast.base.core.Description;
import beast.base.util.Randomizer;
import multitypetree.evolution.tree.MultiTypeNode;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Implementation of generalized merge-split operator described"
        + "by Ewing et al., 2004.")
public class TypeMergeSplitExtended extends MultiTypeTreeOperator {

    @Override
    public double proposal() {
        // Select internal node to operate around:
        int nodeID = mtTree.getLeafNodeCount() + Randomizer.nextInt(mtTree.getInternalNodeCount());
        MultiTypeNode node = (MultiTypeNode)mtTree.getNode(nodeID);

        // Special root move
        if (node.isRoot())
            return rootProposal(node);

        // Regular merge/split moves
        if (Randomizer.nextBoolean())
            return mergeProposal(node);
        else
            return splitProposal(node);
    }
    
    /**
     * Proposal involving root
     * @return log of Hastings Ratio
     */
    private double rootProposal(MultiTypeNode root) {
        double logHR = 0.0;
        
        // Slide change from A to B
        
        MultiTypeNode A, B;
        if (Randomizer.nextBoolean()) {
            A = (MultiTypeNode)root.getLeft();
            B = (MultiTypeNode)root.getRight();
        } else {
            B = (MultiTypeNode)root.getLeft();
            A = (MultiTypeNode)root.getRight();
        }
        
        // Reject if there's no change to slide
        if (A.getChangeCount()==0)
            return Double.NEGATIVE_INFINITY;

        // Remove old change
        A.removeChange(A.getChangeCount()-1);
            
        // Record HR:
        logHR += Math.log(1.0/(root.getHeight() - A.getFinalChangeTime()));
        logHR -= Math.log(1.0/(root.getHeight() - B.getFinalChangeTime()));

        // Select new change time:
        double newTime = B.getFinalChangeTime() +
                Randomizer.nextDouble()*(root.getHeight()-B.getFinalChangeTime());
        
        // Add new change to B:
        B.addChange(A.getFinalType(), newTime);
        
        // Update node type:
        root.setNodeType(A.getFinalType());
        
        return logHR;
    }
    
    /**
     * Merge proposal
     * @return log of Hastings Ratio
     */
    private double mergeProposal(MultiTypeNode node) {
        double logHR = 0.0;
        
        // Select which branch forms the destination branch in this move:
        int destBranch = Randomizer.nextInt(3);
        
        if (destBranch == 2) {
            // Branch is the parent branch (ordinary merge)
            MultiTypeNode left = (MultiTypeNode)node.getLeft();
            MultiTypeNode right = (MultiTypeNode)node.getRight();
            
            // Reject if nothing to merge
            if (left.getChangeCount()==0 || right.getChangeCount()==0) 
                return Double.NEGATIVE_INFINITY;
            
            // Change-removal half of merge
            int changeType = node.getNodeType();
            left.removeChange(left.getChangeCount()-1);
            right.removeChange(right.getChangeCount()-1);
            
            // Reject if types below deleted changes don't match:
            if (left.getFinalType() != right.getFinalType())
                return Double.NEGATIVE_INFINITY;
            
            // HR contribution of reverse move
            logHR += Math.log(1.0/((node.getHeight()-left.getFinalChangeTime())
                     *(node.getHeight()-right.getFinalChangeTime())));
            
            // Maximum time of new change
            double newTimeMax;
            if (node.getChangeCount()>0)
                newTimeMax = node.getChangeTime(0);
            else
                newTimeMax = node.getParent().getHeight();
            
            // HR contribution of forward move
            logHR -= Math.log(1.0/(newTimeMax-node.getHeight()));
            
            // Add new merged change
            double newTime = node.getHeight() +
                    (newTimeMax-node.getHeight())*Randomizer.nextDouble();
            node.insertChange(0, changeType, newTime);
            
            // Update node typ:
            node.setNodeType(left.getFinalType());

        } else {
            // Destination is one of the child branches (generalised move)
            MultiTypeNode destBranchNode = (MultiTypeNode)node.getChild(destBranch);
            MultiTypeNode otherBranchNode = (MultiTypeNode)node.getChild(1-destBranch);

            // Reject if nothing to merge
            if (node.getChangeCount()==0 || otherBranchNode.getChangeCount()==0)
                return Double.NEGATIVE_INFINITY;
            
            // Change removal half of merge
            int changeType = node.getChangeType(0);
            otherBranchNode.removeChange(otherBranchNode.getChangeCount()-1);
            if (otherBranchNode.getFinalType() != node.getChangeType(0))
                return Double.NEGATIVE_INFINITY;
            node.removeChange(0);

            // HR calculation
            double timeMaxParent;
            if (node.getChangeCount()==0)
                timeMaxParent = node.getParent().getHeight();
            else
                timeMaxParent = node.getChangeTime(0);
            double timeMinOther = otherBranchNode.getFinalChangeTime();
            
            logHR += Math.log(1.0/((timeMaxParent-node.getHeight())*(node.getHeight()-timeMinOther)));
            
            // Minimum time of new change
            double newTimeMin = destBranchNode.getFinalChangeTime();
            
            // HR contribution of forward move
            logHR -= Math.log(1.0/(node.getHeight()-newTimeMin));
            
            // Add new merged change
            double newTime = newTimeMin +
                    (node.getHeight()-newTimeMin)*Randomizer.nextDouble();
            destBranchNode.addChange(changeType, newTime);
            
            // Update node type:
            node.setNodeType(changeType);
        }
        
        return logHR;
    }
    
    private double splitProposal(MultiTypeNode node) {
        double logHR = 0.0;
        
        int sourceBranch = Randomizer.nextInt(3);
        
        if (sourceBranch == 2) {
            // Source branch is parent (normal situation)
            
            // Reject if nothing to split:
            if (node.getChangeCount()==0)
                return Double.NEGATIVE_INFINITY;
            
            MultiTypeNode left = (MultiTypeNode)node.getLeft();
            MultiTypeNode right = (MultiTypeNode)node.getRight();
            
            // Change removal half of split move:
            int changeType = node.getChangeType(0);
            node.removeChange(0);

            // HR contribution of reverse move
            double timeMaxParent;
            if (node.getChangeCount()==0)
                timeMaxParent = node.getParent().getHeight();
            else
                timeMaxParent = node.getChangeTime(0);
            logHR += Math.log(1.0/(timeMaxParent-node.getHeight()));
            
            // HR contribution of forward move
            logHR -= Math.log(1.0/((node.getHeight()-left.getFinalChangeTime())
                    *(node.getHeight()-right.getFinalChangeTime())));
            
            // Select new change times
            double newTimeLeft = left.getFinalChangeTime()
                    + (node.getHeight()-left.getFinalChangeTime())*Randomizer.nextDouble();
            double newTimeRight = right.getFinalChangeTime()
                    + (node.getHeight()-right.getFinalChangeTime())*Randomizer.nextDouble();
            
            // Add new changes to child branches
            left.addChange(changeType, newTimeLeft);
            right.addChange(changeType, newTimeRight);
            
            // Update node type
            node.setNodeType(changeType);
            
            
        } else {
            // Source branch is a child (generalized situation)
            
            MultiTypeNode sourceBranchNode = (MultiTypeNode)node.getChild(sourceBranch);
            MultiTypeNode otherBranchNode = (MultiTypeNode)node.getChild(1-sourceBranch);
            
            // Reject if nothing to split:
            if (sourceBranchNode.getChangeCount()==0)
                return Double.NEGATIVE_INFINITY;
            
            // Change removal half of merge:
            sourceBranchNode.removeChange(sourceBranchNode.getChangeCount()-1);
            
            // HR contribution of reverse move:
            logHR += Math.log(1.0/(node.getHeight()-sourceBranchNode.getFinalChangeTime()));
            
            // HR contribution of forward move:
            double maxTimeParent;
            if (node.getChangeCount()==0)
                maxTimeParent = node.getParent().getHeight();
            else
                maxTimeParent = node.getChangeTime(0);
            logHR -= Math.log(1.0/((node.getHeight()-otherBranchNode.getFinalChangeTime())
                    *(maxTimeParent-node.getHeight())));
            
            // Select new change times
            double newTimeNode = node.getHeight()
                    + (maxTimeParent-node.getHeight())*Randomizer.nextDouble();
            
            double newTimeOther = otherBranchNode.getFinalChangeTime()
                    + (node.getHeight()-otherBranchNode.getFinalChangeTime())*Randomizer.nextDouble();

            // Add new changes
            node.insertChange(0, node.getNodeType(), newTimeNode);
            otherBranchNode.addChange(sourceBranchNode.getFinalType(), newTimeOther);
            
            // Update node type
            node.setNodeType(sourceBranchNode.getFinalType());
        }
        
        return logHR;
    }
    
}

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
import beast.core.Input;
import beast.evolution.tree.Node;
import beast.util.Randomizer;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Implements colour change (migration) merge/split move as "
        + "described by Ewing et al., Genetics (2004).")
public class ColourMergeSplit extends MultiTypeTreeOperator {
    
    public Input<Boolean> includeRootInput = new Input<Boolean>("includeRoot",
            "Include Tim's root merge/split moves.  Default false.", false);

    @Override
    public void initAndValidate() { }
    
    @Override
    public double proposal() {
        
        mtTree = multiTypeTreeInput.get();
        tree = mtTree.getUncolouredTree();
        
        Node node;
        do {
            node = tree.getNode(tree.getLeafNodeCount()
                    + Randomizer.nextInt(tree.getInternalNodeCount()));
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
        
        if (mtTree.getChangeCount(node)==0)
            return Double.NEGATIVE_INFINITY;
        
        int colour = mtTree.getChangeColour(node,0);
        
        // Delete old change above node:
        removeChange(node, 0);
        
        // Update node colour:
        setNodeColour(node, colour);

        // Select new change times:
        
        double tminLeft = mtTree.getFinalBranchTime(node.getLeft());
        double tnewLeft = (node.getHeight()-tminLeft)*Randomizer.nextDouble()
                + tminLeft;
        
        double tminRight = mtTree.getFinalBranchTime(node.getRight());
        double tnewRight = (node.getHeight()-tminRight)*Randomizer.nextDouble()
                + tminRight;
        
        // Record time of first migration or coalescence above node
        // for HR calculation:
        double tmax;
        if (mtTree.getChangeCount(node)>0)
            tmax = mtTree.getChangeTime(node, 0);
        else
            tmax = node.getParent().getHeight();

        // Add new changes below node:
        try { 
            addChange(node.getLeft(), colour, tnewLeft);
            addChange(node.getRight(), colour, tnewRight);
        } catch (RecolouringException ex) {
            if (ex.cTree.discardWhenMaxExceeded()) {
                ex.discardMsg();
                return Double.NEGATIVE_INFINITY;
            } else
                ex.throwRuntime();
        }
        
        return Math.log((node.getHeight()-tminRight)*(node.getHeight()-tminLeft))
                - Math.log(tmax-node.getHeight());
    }
    
    private double splitProposalRoot() {
        
        Node root = tree.getRoot();

        // Select new root colour:
        int oldColour = mtTree.getNodeColour(root);
        int colour;
        do {
            colour = Randomizer.nextInt(mtTree.getNColours());
        } while (colour == oldColour);
        
        // Update node colour:
        setNodeColour(root, colour);

        // Select new change times:
        
        double tminLeft = mtTree.getFinalBranchTime(root.getLeft());
        double tnewLeft = (root.getHeight()-tminLeft)*Randomizer.nextDouble()
                + tminLeft;
        
        double tminRight = mtTree.getFinalBranchTime(root.getRight());
        double tnewRight = (root.getHeight()-tminRight)*Randomizer.nextDouble()
                + tminRight;
       
        // Add new changes below node:
        try { 
            addChange(root.getLeft(), colour, tnewLeft);
            addChange(root.getRight(), colour, tnewRight);
        } catch (RecolouringException ex) {
            if (ex.cTree.discardWhenMaxExceeded()) {
                ex.discardMsg();
                return Double.NEGATIVE_INFINITY;
            } else
                ex.throwRuntime();
        }
        
        return Math.log((root.getHeight()-tminRight)*(root.getHeight()-tminLeft)
                *(mtTree.getNColours()-1));

    }
    
    /**
     * Remove a pair of migrations below a coalescence event and create
     * one above it.
     * 
     * @param node Node representing coalescence event.
     * @return log of Hastings ratio
     */
    private double mergeProposal(Node node) {
        
        Node left = node.getLeft();
        Node right = node.getRight();
        
        int leftIdx = mtTree.getChangeCount(left)-1;
        int rightIdx = mtTree.getChangeCount(right)-1;
        
        if (leftIdx<0 || rightIdx<0)
            return Double.NEGATIVE_INFINITY;

        int leftColour = mtTree.getChangeColour(left, leftIdx);
        int rightColour = mtTree.getChangeColour(right, rightIdx);
        
        if (leftColour != rightColour)
            return Double.NEGATIVE_INFINITY;
        
        int leftColourUnder;
        double tminLeft;
        if (leftIdx>0) {
            leftColourUnder = mtTree.getChangeColour(left, leftIdx-1);
            tminLeft = mtTree.getChangeTime(left, leftIdx-1);
        } else {
            leftColourUnder = mtTree.getNodeColour(left);
            tminLeft = left.getHeight();
        }
        
        int rightColourUnder;
        double tminRight;
        if (rightIdx>0) {
            rightColourUnder = mtTree.getChangeColour(right, rightIdx-1);
            tminRight = mtTree.getChangeTime(right, rightIdx-1);
        } else {
            rightColourUnder = mtTree.getNodeColour(right);
            tminRight = right.getHeight();
        }

        if (leftColourUnder != rightColourUnder)
            return Double.NEGATIVE_INFINITY;
        
        double tmax;
        if (mtTree.getChangeCount(node)>0)
            tmax = mtTree.getChangeTime(node,0);
        else
            tmax = node.getParent().getHeight();
        
        removeChange(left, leftIdx);
        removeChange(right, rightIdx);        
        setNodeColour(node, leftColourUnder);
        
        double tnew = Randomizer.nextDouble()*(tmax-node.getHeight())
                + node.getHeight();
        
        try {
            insertChange(node, 0, leftColour, tnew);
        } catch (RecolouringException ex) {
            if (ex.cTree.discardWhenMaxExceeded()) {
                ex.discardMsg();
                return Double.NEGATIVE_INFINITY;
            } else
                ex.throwRuntime();
        }
        
        return Math.log(tmax-node.getHeight())
                - Math.log((node.getHeight()-tminLeft)*(node.getHeight()-tminRight));
    }
    
    private double mergeProposalRoot() {
        
        Node root = tree.getRoot();
             
        Node left = root.getLeft();
        Node right = root.getRight();
        
        int leftIdx = mtTree.getChangeCount(left)-1;
        int rightIdx = mtTree.getChangeCount(right)-1;
        
        if (leftIdx<0 || rightIdx<0)
            return Double.NEGATIVE_INFINITY;

        int leftColour = mtTree.getChangeColour(left, leftIdx);
        int rightColour = mtTree.getChangeColour(right, rightIdx);
        
        if (leftColour != rightColour)
            return Double.NEGATIVE_INFINITY;
        
        int leftColourUnder;
        double tminLeft;
        if (leftIdx>0) {
            leftColourUnder = mtTree.getChangeColour(left, leftIdx-1);
            tminLeft = mtTree.getChangeTime(left, leftIdx-1);
        } else {
            leftColourUnder = mtTree.getNodeColour(left);
            tminLeft = left.getHeight();
        }
        
        int rightColourUnder;
        double tminRight;
        if (rightIdx>0) {
            rightColourUnder = mtTree.getChangeColour(right, rightIdx-1);
            tminRight = mtTree.getChangeTime(right, rightIdx-1);
        } else {
            rightColourUnder = mtTree.getNodeColour(right);
            tminRight = right.getHeight();
        }

        if (leftColourUnder != rightColourUnder)
            return Double.NEGATIVE_INFINITY;
        
        
        removeChange(left, leftIdx);
        removeChange(right, rightIdx);        
        setNodeColour(root, leftColourUnder);
        
        return -Math.log((root.getHeight()-tminRight)*(root.getHeight()-tminLeft)
                *(mtTree.getNColours()-1));
    }
    
}

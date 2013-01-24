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
import beast.evolution.tree.Node;
import beast.util.Randomizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Implements colour change (migration) merge/split move as "
        + "described by Ewing et al., Genetics (2004).")
public class ColourMergeSplit extends ColouredTreeOperator {

    @Override
    public double proposal() {
        
        cTree = colouredTreeInput.get();
        tree = cTree.getUncolouredTree();
        
        Node node;
        do {
            node = tree.getNode(tree.getLeafNodeCount()
                    + Randomizer.nextInt(tree.getInternalNodeCount()));
        } while (node.isRoot());
        
        // Randomly select a merge or split proposal:
        if (Randomizer.nextDouble()<0.5)
            return splitProposal(node);
        else
            return mergeProposal(node);
    
    }
    
    /**
     * Remove a migration event above a coalescence event and create two
     * below it.
     * 
     * @param node Node representing coalescence event.
     * @return log of Hastings ratio
     */
    private double splitProposal(Node node) {
        
        if (cTree.getChangeCount(node)==0)
            return Double.NEGATIVE_INFINITY;
        
        int colour = cTree.getChangeColour(node,0);
        
        // Delete old change above node:
        removeChange(node, 0);
        
        // Update node colour:
        setNodeColour(node, colour);

        // Select new change times:
        
        double tminLeft = cTree.getFinalBranchTime(node);
        double tnewLeft = (node.getHeight()-tminLeft)*Randomizer.nextDouble()
                + tminLeft;
        
        double tminRight = cTree.getFinalBranchTime(node);
        double tnewRight = (node.getHeight()-tminRight)*Randomizer.nextDouble()
                + tminRight;
        
        // Record time of first migration or coalescence above node
        // for HR calculation:
        double tmax;
        if (cTree.getChangeCount(node)>0)
            tmax = cTree.getChangeTime(node, 0);
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
        
        int leftIdx = cTree.getChangeCount(left)-1;
        int rightIdx = cTree.getChangeCount(right)-1;
        
        if (leftIdx<0 || rightIdx<0)
            return Double.NEGATIVE_INFINITY;

        int leftColour = cTree.getChangeColour(left, leftIdx);
        int rightColour = cTree.getChangeColour(right, rightIdx);
        
        if (leftColour != rightColour)
            return Double.NEGATIVE_INFINITY;
        
        int leftColourUnder;
        if (leftIdx>0)
            leftColourUnder = cTree.getChangeColour(left, leftIdx-1);
        else
            leftColourUnder = cTree.getNodeColour(left);
        
        int rightColourUnder;
        if (rightIdx>0)
            rightColourUnder = cTree.getChangeColour(right, rightIdx-1);
        else
            rightColourUnder = cTree.getNodeColour(right);

        if (leftColourUnder != rightColourUnder)
            return Double.NEGATIVE_INFINITY;
        
        double tmax;
        if (cTree.getChangeCount(node)>0)
            tmax = cTree.getChangeTime(node,0);
        else
            tmax = node.getParent().getHeight();
        
        removeChange(node, leftIdx);
        removeChange(node, rightIdx);        
        setNodeColour(node, leftColourUnder);
        
        double tnew = Randomizer.nextDouble()*(tmax-node.getHeight())
                + node.getHeight();
        
        try {
            insertChange(node, 0, leftColourUnder, tnew);
        } catch (RecolouringException ex) {
            if (ex.cTree.discardWhenMaxExceeded()) {
                ex.discardMsg();
                return Double.NEGATIVE_INFINITY;
            } else
                ex.throwRuntime();
        }
        
        return 0.0;
    }
    
}

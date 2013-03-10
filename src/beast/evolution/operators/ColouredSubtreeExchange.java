/*
 * Copyright (C) 2012 Tim Vaughan <tgvaughan@gmail.com>
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
@Description("Subtree/branch exchange operator for coloured trees.  This"
        + " is the `uniformized recolouring' variant where new branch colourings"
        + " are selected using random walk conditional on the colours at both"
        + " ends of the branch.")
public class ColouredSubtreeExchange extends UniformizationRecolourOperator {
    
    public Input<Boolean> isNarrowInput = new Input<Boolean>("isNarrow",
            "Whether or not to use narrow exchange. (Default true.)", true);

    @Override
    public void initAndValidate() { }
    
    @Override
    public double proposal() {
        mtTree = multiTypeTreeInput.get();
        tree = mtTree.getUncolouredTree();
        
        double logHR = 0.0;

        // Select source and destination nodes:
        
        Node srcNode, srcNodeParent, destNode, destNodeParent;
        if (isNarrowInput.get()) {
            
            // Narrow exchange selection:
            do {
                srcNode = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
            } while (srcNode.isRoot() || srcNode.getParent().isRoot());
            srcNodeParent = srcNode.getParent();            
            destNode = getOtherChild(srcNodeParent.getParent(), srcNodeParent);
            destNodeParent = destNode.getParent();
            
        } else {
            
            // Wide exchange selection:
            do {
                srcNode = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
            } while (srcNode.isRoot());
            srcNodeParent = srcNode.getParent();
            
            do {
                destNode = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
            } while(destNode == srcNode
                    || destNode.isRoot()
                    || destNode.getParent() == srcNode.getParent());
            destNodeParent = destNode.getParent();
        }
        
        // Reject if substitution would result in negative branch lengths:
        if (destNode.getHeight()>srcNodeParent.getHeight()
                || srcNode.getHeight()>destNodeParent.getHeight())
            return Double.NEGATIVE_INFINITY;
        
        // Record probability of old colours:
        logHR += getBranchColourProb(srcNode) + getBranchColourProb(destNode);
        
        // Make changes to tree topology:
        replace(srcNodeParent, srcNode, destNode);
        replace(destNodeParent, destNode, srcNode);
        
        // Recolour branches involved:
        try {
            logHR -= recolourBranch(srcNode) + recolourBranch(destNode);
        } catch (RecolouringException ex) {
            if (mtTree.discardWhenMaxExceeded()) {
                ex.discardMsg();
                return Double.NEGATIVE_INFINITY;
            } else
                ex.throwRuntime();
        }
        
        return logHR;
    }
    
}

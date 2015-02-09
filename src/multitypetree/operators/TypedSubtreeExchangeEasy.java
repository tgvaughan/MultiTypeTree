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
package multitypetree.operators;

import beast.core.Description;
import beast.core.Input;
import beast.evolution.tree.Node;
import beast.util.Randomizer;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Subtree/branch exchange operator for coloured trees.  This"
        + " is the `no recolouring' variant where the new topology is selected"
        + " irrespective of the colouring of the branches involved.  Used for"
        + " testing Ewing et al.'s sampler moves.")
public class TypedSubtreeExchangeEasy extends MultiTypeTreeOperator {
    
    public Input<Boolean> isNarrowInput = new Input<>("isNarrow",
            "Whether or not to use narrow exchange. (Default true.)", true);

    @Override
    public double proposal() {
        // Select source and destination nodes:
        
        Node srcNode, srcNodeParent, destNode, destNodeParent;
        if (isNarrowInput.get()) {
            
            // Narrow exchange selection:
            do {
                srcNode = mtTree.getNode(Randomizer.nextInt(mtTree.getNodeCount()));
            } while (srcNode.isRoot() || srcNode.getParent().isRoot());
            srcNodeParent = srcNode.getParent();            
            destNode = getOtherChild(srcNodeParent.getParent(), srcNodeParent);
            destNodeParent = destNode.getParent();
            
        } else {
            
            // Wide exchange selection:
            do {
                srcNode = mtTree.getNode(Randomizer.nextInt(mtTree.getNodeCount()));
            } while (srcNode.isRoot());
            srcNodeParent = srcNode.getParent();
            
            do {
                destNode = mtTree.getNode(Randomizer.nextInt(mtTree.getNodeCount()));
            } while(destNode == srcNode
                    || destNode.isRoot()
                    || destNode.getParent() == srcNodeParent);
            destNodeParent = destNode.getParent();
            
            // Explicitly reject outrageous node selections:
            // (Dangerous to make this a condition of destNode selection,
            // as doing so can lead to infinite loops.)
            if (srcNodeParent == destNode || destNodeParent == srcNode)
                return Double.NEGATIVE_INFINITY;
        }
        
        // Reject outright if substitution would result in negative branch
        // lengths:
        if (destNode.getHeight()>srcNodeParent.getHeight()
                || srcNode.getHeight()>destNodeParent.getHeight())
            return Double.NEGATIVE_INFINITY;
        
        // Make changes to tree topology:
        replace(srcNodeParent, srcNode, destNode);
        replace(destNodeParent, destNode, srcNode);
        
        // Force rejection if resulting multi-type tree invalid:
        if (!mtTree.isValid())
            return Double.NEGATIVE_INFINITY;
        
        return 0.0;
    }    
    
}

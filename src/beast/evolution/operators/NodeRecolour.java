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
import beast.evolution.tree.Node;
import beast.util.Randomizer;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Recolours a randomly chosen node and its attached branches. "
        + "This variant uses the uniformization branch recolouring procedure.")
public class NodeRecolour extends UniformizationRecolourOperator {
    
    @Override
    public void initAndValidate() { }

    @Override
    public double proposal() {
        cTree = colouredTreeInput.get();
        tree = cTree.getUncolouredTree();
        
        double logHR = 0.0;
        
        // Select node:
        Node node;
        do {
            node = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
        } while (node.isLeaf());
        
        // Record probability of current colours along attached branches:
        if (!node.isRoot())
            logHR += getBranchColourProb(node);

        logHR += getBranchColourProb(node.getLeft())
                + getBranchColourProb(node.getRight());
        
        // Select new node colour:
        setNodeColour(node, Randomizer.nextInt(cTree.getNColours()));
        
        // Recolour attached branches:
        try {
            if (!node.isRoot())
                logHR -= recolourBranch(node);

            logHR -= recolourBranch(node.getLeft())
                    + recolourBranch(node.getRight());
        } catch (RecolouringException ex) {
            if (cTree.discardWhenMaxExceeded()) {
                ex.discardMsg();
                return Double.NEGATIVE_INFINITY;
            } else
                ex.throwRuntime();
        }
        
        return logHR;
    }
    
}

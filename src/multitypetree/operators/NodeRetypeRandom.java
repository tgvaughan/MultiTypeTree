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
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Retypes a randomly chosen node and its attached branches. "
        + "This variant uses an unconditioned random walk for branch retyping.")
public class NodeRetypeRandom extends RandomRetypeOperator {
    
    @Override
    public void initAndValidate() { }

    @Override
    public double proposal() {
        mtTree = multiTypeTreeInput.get();
        
        double logHR = 0.0;
        
        // Select node:
        Node node;
        do {
            node = mtTree.getNode(Randomizer.nextInt(mtTree.getNodeCount()));
        } while (node.isLeaf());
        
        // Record probability of current types along attached branches:
        if (!node.isRoot())
            logHR += getBranchTypeProb(node);

        logHR += getBranchTypeProb(node.getLeft())
                + getBranchTypeProb(node.getRight());
        
        // Select new node type:
        ((MultiTypeNode)node).setNodeType(Randomizer.nextInt(mtTree.getNTypes()));
        
        // Retype attached branches, forcing reject if inconsistent:
        if (!node.isRoot()) {
            logHR -= retypeBranch(node);

            if (((MultiTypeNode)node).getFinalType() != ((MultiTypeNode)node.getParent()).getNodeType())
                return Double.NEGATIVE_INFINITY;
        }

        logHR -= retypeBranch(node.getLeft())
                + retypeBranch(node.getRight());
       
        if ((((MultiTypeNode)node.getLeft()).getFinalType() != ((MultiTypeNode)node).getNodeType())
                || (((MultiTypeNode)node.getRight()).getFinalType() != ((MultiTypeNode)node).getNodeType()))
            return Double.NEGATIVE_INFINITY;
        
        // WHY IS THIS NECESSARY!?
        node.makeDirty(Tree.IS_DIRTY);
        node.getLeft().makeDirty(Tree.IS_DIRTY);
        node.getRight().makeDirty(Tree.IS_DIRTY);
        
        return logHR;
    }
    
}

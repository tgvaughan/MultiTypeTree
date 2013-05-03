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
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.Node;
import beast.util.Randomizer;

/**
 * 
 * @author Denise Kuhnert
 */
@Description("Randomly selects true internal tree node (i.e. not the root) and"
        + " moves node height uniformly in interval restricted by the node's"
        + " parent and children.")
public class MultiTypeUniform extends MultiTypeTreeOperator {

    @Override
    public void initAndValidate() {
    }

    /**
     * Change the node height and return the hastings ratio.
     *
     * @return log of Hastings Ratio
     */
    @Override
    public double proposal() {

        mtTree = multiTypeTreeInput.get();

        // randomly select internal node
        final int nNodeCount = mtTree.getNodeCount();
        Node node;
        do {
            final int iNodeNr = nNodeCount / 2 + 1 + Randomizer.nextInt(nNodeCount / 2);
            node = mtTree.getNode(iNodeNr);
        } while (node.isRoot() || node.isLeaf());

        // upper is first node type change, if there is one, otherwise it's parent height
        final double fUpper = (((MultiTypeNode)node).getChangeCount() > 0)
                ? ((MultiTypeNode)node).getChangeTime(0)
                : node.getParent().getHeight();
        
        // lower is maximum of childrens' last node type change, if there are any, otherwise of their heights         
        Node left = node.getLeft();
        Node right = node.getRight();
        int lCount = ((MultiTypeNode)left).getChangeCount();
        int rCount = ((MultiTypeNode)right).getChangeCount();
        final double fLower = Math.max(
                (lCount>0 ? (((MultiTypeNode)left).getChangeTime(lCount-1)) : left.getHeight()),
                (rCount>0 ? (((MultiTypeNode)right).getChangeTime(rCount-1)) : right.getHeight()));

        final double newValue = (Randomizer.nextDouble() * (fUpper - fLower)) + fLower;
        node.setHeight(newValue);

        return 0.0;
    }

}

/*
 * Copyright (C) 2012 Tim Vaughan
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
package beast.evolution.operator;

import beast.core.Description;
import beast.evolution.tree.ColouredTree;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;

/**
 * Wilson-Balding branch swapping operator applied to coloured trees,
 * modified so that only those moves which preserve the validity of
 * the colouring are proposed.
 *
 * @author Tim Vaughan
 */
@Description("Implements the unweighted Wilson-Balding branch"
		+ "swapping move.  This move is similar to one proposed by WILSON"
		+ "and BALDING 1998 and involves removing a subtree and"
		+ "re-attaching it on a new parent branch. " +
        "See <a href='http://www.genetics.org/cgi/content/full/161/3/1307/F1'>picture</a>."
		+ "This version is altered to perform only those moves which preserve"
		+ "the validity of the tree colouring.")
public class ColouredWilsonBalding extends ColouredTreeOperator {

	@Override
	public void initAndValidate() {};

	@Override
	public double proposal() {
		ColouredTree cTree = colouredTreeInput.get();
		Tree tree = cTree.getUncolouredTree();

		// Choose node at base of random edge which does not
		// involve the root:

		Node i; // Node at bottom of edge.
		do {
			i = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
		} while (i.isRoot() || i.getParent().isRoot());

		int icolour = cTree.getFinalBranchColour(i);
		double minNewTime = cTree.getFinalBranchTime(i);
		Node iP = i.getParent();

		// Choose coloured sub-edge having the same colour as the linage
		// at i and with the time at the top of the edge being greater
		// than the time at i.

		Node j;		// Node at bottom of edge.
		double newTime;
		do {
			j = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
		} while (j.isRoot()
				|| j.getParent()==iP
				|| (newTime = cTree.chooseTimeWithColour(j, minNewTime, icolour))<0);

		return Double.NEGATIVE_INFINITY;
	}
	
}
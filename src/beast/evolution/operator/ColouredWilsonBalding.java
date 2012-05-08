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

		// Choose non-root node at base of random edge.

		Node i; // Node at bottom of edge.
		do {
			i = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
		} while (i.isRoot());
		Node iP = i.getParent();

		int iColour = cTree.getFinalBranchColour(i);
		double minNewTime = cTree.getFinalBranchTime(i);

		// Choose coloured sub-edge having the same colour as the linage
		// at i and with the time at the top of the edge being greater
		// than the time at i.

		Node j,jP; // Node at bottom of edge.
		double newRange = 0;
		do {
			j = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
			jP = j.getParent();
		} while (j==i ||
				(!j.isRoot() && ((jP == iP)
				|| (newRange = cTree.getColouredSegmentLength(j, minNewTime, iColour))<0)));

		if (iP.isRoot()) {

			// TODO: calculate HR
			double logHR = Double.NEGATIVE_INFINITY;

			// TODO: implement topology change

			return logHR;

		} else if (j.isRoot()) {

			// TODO: calculate HR
			double logHR = Double.NEGATIVE_INFINITY;

			// TODO: implement topology change

			return logHR;
		}

		// Simple case where root is not involved:

		// Calculate HR:
		Node CiP = getOtherChild(iP, i);
		double oldRange = cTree.getColouredSegmentLength(CiP, minNewTime, iColour);
		double logHR = Math.log(newRange/oldRange);

		// Implement topology change
		double newTime = cTree.chooseTimeWithColour(j, minNewTime, iColour, newRange);
		replace(iP.getParent(), iP, CiP);
		replace(iP, CiP, j);
		replace(jP, j, iP);
		
		return logHR;
	}
}
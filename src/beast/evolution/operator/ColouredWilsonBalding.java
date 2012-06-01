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
import beast.core.Input;
import beast.core.Input.Validate;
import beast.evolution.migrationmodel.MigrationModel;
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
		+ "This version generates new colouring along branches altered by"
		+ "the operator.")
public class ColouredWilsonBalding extends ColouredTreeOperator {

	public Input<MigrationModel> migrationModel = new Input<MigrationModel>(
			"migrationModel",
			"Migration model to use as prior for colouring branches",
			Validate.REQUIRED);

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

		int startColour = cTree.getNodeColour(i);
		double minNewTime = i.getHeight();

		Node j,jP; // Node at bottom of edge.
		double newRange = 0;
		do {
			j = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
			jP = j.getParent();
		} while (j==i || jP.getHeight()<i.getHeight() ||
				(!j.isRoot() && ((jP == iP)
				|| (newRange = cTree.getColouredSegmentLength(j, minNewTime, startColour))<0)));

		if (iP.isRoot()) {

			// TODO: calculate HR
			Node CiP = getOtherChild(iP, i);

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
	
		return Double.NEGATIVE_INFINITY;
	}
}

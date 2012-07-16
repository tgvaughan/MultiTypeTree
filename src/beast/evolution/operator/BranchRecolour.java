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
package beast.evolution.operator;

import beast.core.Description;
import beast.core.Input;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.Node;
import beast.math.GammaFunction;
import beast.util.PoissonRandomizer;
import beast.util.Randomizer;

/**
 * Operator to randomly recolour a branch on a coloured tree.
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Recolour a branch of a coloured tree.")
public class BranchRecolour extends ColouredTreeOperator {
	
    public Input<Double> muInput = new Input<Double>("mu",
			"Mean rate of colour change along branch.", Input.Validate.REQUIRED);

	private double mu;

	@Override
	public void initAndValidate() { }

	@Override
	public double proposal() {

		cTree = colouredTreeInput.get();
		tree = cTree.getUncolouredTree();
		mu = muInput.get();

		// Randomly select branch to recolour:
		Node node;
		do {
			node = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
		} while (node.isRoot());

		// Record old change count:
		int nChangesOld = cTree.getChangeCount(node);

		// Recolour branch:
		recolourBranch(node);

		// Record new change count:
		int nChangesNew = cTree.getChangeCount(node);

		// Calculate HR:
		double logHR = (nChangesOld-nChangesNew)*Math.log(mu/cTree.getNColours())
				+ 2*(GammaFunction.lnGamma(nChangesNew+1)
					- GammaFunction.lnGamma(nChangesOld+1));

		return logHR;
	}

	/**
	 * Recolour branch above node with random colour changes.
	 * 
	 * @param node 
	 */
	private void recolourBranch(Node node) {

		// Clear current changes:
		setChangeCount(node, 0);

		double t = node.getHeight();
		double tP = node.getParent().getHeight();
		int lastCol = cTree.getNodeColour(node);

		while (t<tP) {

			t += Randomizer.nextExponential(mu);
			if (t<tP) {
				// Select next colour:
				int newCol;
				do {
					newCol = Randomizer.nextInt(cTree.getNColours());
				} while (newCol == lastCol);

				// Record change:
				addChange(node, newCol, t);

				// Update last colour:
				lastCol = newCol;
			}
		}
	}

}

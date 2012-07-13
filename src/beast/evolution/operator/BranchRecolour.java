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
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Recolour a branch of a coloured tree.")
public class BranchRecolour extends ColouredTreeOperator {
	
    public Input<RealParameter> muInput = new Input<RealParameter>("mu",
			"Mean rate of colour change along branch.", Input.Validate.REQUIRED);

	private double mu;

	@Override
	public double proposal() {

		cTree = colouredTreeInput.get();
		tree = cTree.getUncolouredTree();
		mu = muInput.get().getValue();

		// Randomly select branch to recolour:
		Node node;
		do {
			node = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
		} while (!node.isRoot());

		// Determine probability of choosing current colouring:
		double logOldProb = getLogColourProb(node);

		// Recolour branch:
		recolourBranch(node);

		// Find probability of choosing new colouring:
		double logNewProb = getLogColourProb(node);

		// Calculate HR:
		

		return 0.0;
	}

	/**
	 * Determine log probability of colouring on branch starting at node.
	 * 
	 * @param node
	 * @return log probability
	 */
	private double getLogColourProb(Node node) {

		int nChanges = cTree.getChangeCount(node);
		double T = node.getParent().getHeight() - node.getHeight();

		// P(branch) = P(colours|nchanges)P(nchanges)

		double logPnChanges = -mu*T + nChanges*Math.log(mu)
				- GammaFunction.lnGamma(nChanges+1);

		double logPcolours = -nChanges*Math.log(cTree.getNColours()-1);

		return logPcolours + logPnChanges;
	}

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

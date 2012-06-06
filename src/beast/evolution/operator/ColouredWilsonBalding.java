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
import beast.evolution.substitutionmodel.DefaultEigenSystem;
import beast.evolution.substitutionmodel.EigenDecomposition;
import beast.evolution.substitutionmodel.EigenSystem;
import beast.evolution.tree.ColouredTree;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;
import java.util.Arrays;

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

	public Input<MigrationModel> migrationModelInput = new Input<MigrationModel>(
			"migrationModel",
			"Migration model to use as prior for colouring branches",
			Validate.REQUIRED);

	@Override
	public void initAndValidate() {};

	@Override
	public double proposal() {
		cTree = colouredTreeInput.get();
		tree = cTree.getUncolouredTree();

		Node i = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));

		if (i.isRoot())
			return Double.NEGATIVE_INFINITY;

		Node iP = i.getParent();

		Node j;
		do {
			j = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
		} while (j==i);

		Node jP = j.getParent(); // may be null

		if (iP == jP || iP == j || (jP != null && jP.getHeight()<i.getHeight()))
			return Double.NEGATIVE_INFINITY;

		if (iP.isRoot()) {

			return Double.NEGATIVE_INFINITY;
		}

		if (j.isRoot()) {

			return Double.NEGATIVE_INFINITY;
		}

		// Simple case where root is not involved:

		// Select height of new node:
		double newTimeMin = Math.max(i.getHeight(), j.getHeight());
		double newTimeMax = jP.getHeight();
		double newTime = newTimeMin +
				Randomizer.nextDouble()*(newTimeMax-newTimeMin);

		// Implement tree changes:
		disconnectBranch(i);
		connectBranch(i, j, newTime);
		Node iP_prime = i.getParent();

		// Recolour new branch:
		recolour(i);

		// Calculate Hastings ratio:
	
		return Double.NEGATIVE_INFINITY;
	}

	/**
	 * Recolour branch between node and its parent conditional on the
	 * starting initial and final colours of the branch.
	 * 
	 * @param node
	 */
	private void recolour(Node node) {

		// Obtain initial and final conditions:

		int initialColour = cTree.getNodeColour(node);
		double initialTime = node.getHeight();

		Node parent = node.getParent();
		int finalColour = cTree.getNodeColour(parent);
		double finalTime = parent.getHeight();

		// Uniformize birth-death process and create sequence of virtual events:

		double[] times = getTimes(initialTime, finalTime,
				initialColour, finalColour);

		// Use forward-backward algorithm to determine colour changes:
		int[] colours = getColours(initialTime, finalTime,
				initialColour, finalColour, times);

		// Record new colour change events:
		cTree.setChangeCount(node, times.length);
		cTree.setChangeColours(node, colours);
		cTree.setChangeTimes(node, times);

	}

	/**
	 * Apply "Uniformization" algorithm to determine colour change times.
	 * See Fearnhead and Sherlock, J. R. Statist. Soc. B (2006).
	 * 
	 * @param initialTime
	 * @param finalTime
	 * @param initialColour
	 * @param finalColour
	 * @return Array of colour change times.
	 */
	private double[] getTimes(double initialTime, double finalTime,
			int initialColour, int finalColour) {

		MigrationModel model = migrationModelInput.get();

		int nColours = cTree.getNColours();


		// Obtain number of events:

		double T = finalTime - initialTime;

		double expTQ = 0.0;
		EigenDecomposition eig = model.getQdecomp();
		for (int s=0; s<eig.getEigenValues().length; s++) {
			expTQ += eig.getEigenVectors()[s*nColours+initialColour]
					*eig.getEigenVectors()[s*nColours+finalColour]
					*Math.exp(T*eig.getEigenValues()[s]);
		}

		double g = expTQ*Randomizer.nextDouble();

		double mu = model.getVirtTransRate();
		double poissonFactor = Math.exp(-mu*T);

		EigenDecomposition eigUnif = model.getUnifQdecomp();

		int n=0;
		double cumulant = 0;
		do {
			double likelihood;
			if (n>0) {
				poissonFactor *= mu*T/(double)n;

				likelihood = 0.0;
				for (int s=0; s<eigUnif.getEigenValues().length; s++) {
					likelihood += eigUnif.getEigenVectors()[s*nColours+initialColour]
							*eigUnif.getEigenVectors()[s*nColours+finalColour]
							*Math.pow(eigUnif.getEigenValues()[s],n);
				}
			} else {
				likelihood = 1.0;
			}

			cumulant += likelihood*poissonFactor;
			n++;

		} while (cumulant<g);
		n--;

		double[] eventTimes = new double[n];


		// Position events randomly along branch:

		for (int i=0; i<n; i++)
			eventTimes[i] = Randomizer.nextDouble()*T + initialTime;
		Arrays.sort(eventTimes);

		return eventTimes;
	}

	/**
	 * Apply forward-backward algorithm to determine colour changes conditional
	 * on initial and final colours.
	 * See Fearnhead and Sherlock, J. R. Statist. Soc. B (2006).
	 * 
	 * @param initialTime
	 * @param finalTime
	 * @param initialColour
	 * @param finalColour
	 * @param times
	 * @return Array containing colours following each change.
	 */
	private int[] getColours(double initialTime, double finalTime,
			int initialColour, int finalColour, double[] times) {
		throw new UnsupportedOperationException("Not yet implemented");
	}
}

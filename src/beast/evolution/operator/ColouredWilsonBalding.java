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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

		// Record probability of old branch colouring (needed for HR):
		double oldColourProb = getPathProb(i);

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
		recolourBranch(i);

		// Calculate probability of new branch colouring:
		double newColourProb = getPathProb(i);

		// Calculate Hastings ratio:
		Node CiP = getOtherChild(iP, i);
		Node PiP = iP.getParent();

		double HR = (jP.getHeight()-Math.max(i.getHeight(),j.getHeight()))
				/(PiP.getHeight()-Math.max(i.getHeight(),CiP.getHeight()))
				*oldColourProb/newColourProb;

		return HR;
	}

	/**
	 * Recolour branch between node and its parent conditional on the
	 * starting initial and final colours of the branch.
	 * 
	 * @param node
	 */
	private void recolourBranch(Node node) {

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


		// Remove events which don't actually change colours:
		// (Would love to put this in a private method, but Java's mysterious
		// pass-by-VALUE semantics have me stumped.)

		List<Double> newTimesList = new ArrayList<Double>(); 
		List<Integer> newColoursList = new ArrayList<Integer>();

		int lastColour = initialColour;
		for (int i=0; i<times.length; i++) {
			if (colours[i] != lastColour) {
				newTimesList.add(times[i]);
				newColoursList.add(colours[i]);
				lastColour = colours[i];
			}
		}

		times = new double[newTimesList.size()];
		colours = new int[newColoursList.size()];

		for (int i=0; i<newTimesList.size(); i++) {
			times[i] = newTimesList.get(i);
			colours[i] = newColoursList.get(i);
		}

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

		EigenDecomposition eig = model.getQdecomp();
		double expTQ = getMatrixExp(eig, initialColour, finalColour, T);
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
				likelihood = getMatrixPower(eigUnif, initialColour, finalColour, n);
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

		MigrationModel model = migrationModelInput.get();

		int n = times.length;
		int [] colours = new int[n];
		int nColours = cTree.getNColours();

		EigenDecomposition eigUnif = model.getUnifQdecomp();

		int s = initialColour;
		for (int i=0; i<n; i++) {

			double denom = getMatrixPower(eigUnif, s, finalColour, n-i+2);
			double g = denom*Randomizer.nextDouble();

			int sPrime;
			double acc = 0.0;
			for (sPrime = 0; sPrime<nColours; sPrime++) {
				acc += model.getQ()[s][sPrime]*getMatrixPower(eigUnif,
						s, finalColour, n-i+1);

				if (acc>g)
					break;
			}

			s = sPrime;
			colours[i] = s;
		}

		return colours;
	}

	/**
	 * Use eigenvector decomposition of a matrix to evaluate elements
	 * of its powers.
	 * 
	 * @param decomp
	 * @param i Row index
	 * @param j Column index
	 * @param exponent Exponent to raise matrix to.
	 * @return Element (i,j) of A^n.
	 */
	private double getMatrixPower(EigenDecomposition decomp,
			int i, int j, double exponent) {

		int nColours = cTree.getNColours();

		double res = 0.0;
		for (int l=0; l<decomp.getEigenValues().length; l++) {
			res += decomp.getEigenVectors()[l*nColours+i]
					*decomp.getEigenVectors()[l*nColours+j]
					*Math.pow(decomp.getEigenValues()[l], exponent);
		}

		return res;
	}

	/**
	 * Use eigenvector decomposition of a matrix to evaluate elements
	 * an exponentiated matrix.
	 * 
	 * @param decomp Eigenvector decomposition of matrix A.
	 * @param i Row index
	 * @param j Column index
	 * @param c Constant factor to appear before matrix.
	 * @return Element (i,j) of exp(c*A).
	 */
	private double getMatrixExp(EigenDecomposition decomp,
			int i, int j, double c) {

		int nColours = cTree.getNColours();

		double res = 0.0;
		for (int l=0; l<decomp.getEigenValues().length; l++) {
			res += decomp.getEigenVectors()[l*nColours+i]
					*decomp.getEigenVectors()[l*nColours+j]
					*Math.exp(c*decomp.getEigenValues()[l]);
		}

		return res;
	}

	/**
	 * Calculate probability of the colouration (migratory path) along the
	 * branch starting at node, given the initial and final colours of the
	 * branch.
	 * 
	 * @param node
	 * @return 
	 */
	private double getPathProb(Node node) {
		double[][] Q = migrationModelInput.get().getQ();

		double initialTime = node.getHeight();
		double finalTime = node.getParent().getHeight();
		double T = finalTime - initialTime;

		// Use the inverse maximum overall transition rate to fix the interval
		// size:
		int nIntervals = 10*(int)(T/(migrationModelInput.get().getVirtTransRate()));
		double deltaT = T/(double)nIntervals;

		int thisChange = -1;
		int thisColour = cTree.getNodeColour(node);
		int lastColour = thisColour;

		double prob = 1.0;
		for (int i=0; i<nIntervals; i++) {

			double thisTime = initialTime + deltaT*i;

			// If thisTime > nextChangeTime, update colour.
			while (thisChange+1<cTree.getChangeCount(node)
					&& thisTime>cTree.getChangeTime(node,thisChange+1)) {
				thisChange++;
			}

			if (thisChange>=0) {
				lastColour = thisColour;
				thisColour = cTree.getChangeColour(node, thisChange);
			}
			
			if (thisColour == lastColour)
				prob *= 1+deltaT*Q[thisColour][thisColour];
			else
				prob *= deltaT*Q[thisColour][lastColour];

		}

		return prob;
	}

	/**
	 * Main method for debugging.
	 * 
	 * @param args 
	 */
	public static void main (String[] args) {

		// TODO: Test branch recolouring algorithm.

	}

}
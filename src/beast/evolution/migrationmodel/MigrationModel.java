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
package beast.evolution.migrationmodel;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Plugin;
import beast.core.parameter.RealParameter;
import beast.evolution.substitutionmodel.DefaultEigenSystem;
import beast.evolution.substitutionmodel.EigenDecomposition;
import beast.evolution.substitutionmodel.EigenSystem;

/**
 * Basic plugin describing a simple Markovian migration model, for use by
 * ColouredTree operators and likelihoods.  Note that this class and package
 * are just stubs.  We expect to have something similar to the
 * SubstitutionModel class/interface eventually.
 *
 * @author Tim Vaughan
 */
@Description("Basic plugin describing a simple Markovian migration model.")
public class MigrationModel extends Plugin {

	public Input<RealParameter> rateMatrixInput = new Input<RealParameter>(
			"rateMatrix", "Migration rate matrix", Validate.REQUIRED);

	public Input<RealParameter> popSizesInput = new Input<RealParameter>(
			"popSizes",
			"Deme population sizes.",
			Validate.REQUIRED);

	protected RealParameter rateMatrix, popSizes;
	protected double[][] Q, unifQ;
	protected double mu;
	protected EigenDecomposition Qdecomp, unifQdecomp;

	public MigrationModel() { }

	@Override
	public void initAndValidate() {
		rateMatrix = rateMatrixInput.get();
		popSizes = popSizesInput.get();

		int nColours = rateMatrix.getMinorDimension1();
		Q = new double[nColours][nColours];

		mu = 0;
		for (int i=0; i<nColours; i++) {
			double qi = 0.0;
			for (int j=0; j<nColours; j++) {
				if (i != j) {
					double Qij = rateMatrix.getMatrixValue(i,j);
					Q[i][j] = Qij;
					qi += Qij;
				}
			}
			Q[i][i] = -qi;

			if (qi>mu)
				mu = qi;
		}

		EigenSystem eig = new DefaultEigenSystem(nColours);
		Qdecomp = eig.decomposeMatrix(Q);

		unifQ = new double[nColours][nColours];
		for (int i=0; i<nColours; i++) {
			for (int j=0; j<nColours; j++) {
				unifQ[i][j] = Q[i][j]/mu;
				if (i==j)
					unifQ[i][i] += 1.0;
			}
		}

		unifQdecomp = eig.decomposeMatrix(unifQ);

	}

	/**
	 * Obtain rate matrix for migration model.
	 * 
	 * @return Rate matrix (RealParameter).
	 */
	public RealParameter getRateMatrix() {
		return rateMatrix;
	}

	/**
	 * Obtain rate matrix as a plain array matrix.
	 * 
	 * @return Rate matrix(double[][])
	 */
	public double[][] getQ() {
		return Q;
	}

	/**
	 * Obtain uniformized rate matrix as a plain array matrix.
	 * 
	 * @return Rate matrix(double[][])
	 */
	public double[][] getUnifQ() {
		return unifQ;
	}

	/**
	 * Obtain eigenvector decomposition of rate matrix.
	 * 
	 * @return EigenSystem object.
	 */
	public EigenDecomposition getQdecomp() {
		return Qdecomp;
	}

	/**
	 * Obtain eigenvector decomposition of "uniformized" rate matrix.
	 * 
	 * @return EigenSystem object.
	 */
	public EigenDecomposition getUnifQdecomp() {
		return unifQdecomp;
	}

	/**
	 * Obtain virtual transition rate for uniformized process.
	 * 
	 * @return Rate of occurrence virtual events.
	 */
	public double getVirtTransRate() {
		return mu;
	}

	/**
	 * Obtain array of effective population sizes, indexed by colour.
	 * 
	 * @return Population size array.
	 */
	public RealParameter getPopSizes() {
		return popSizes;
	}
}

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
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.EigenvalueDecomposition;

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

	private RealParameter rateMatrix, popSizes;
	private double totalPopSize;
	private double mu;
	private DoubleMatrix2D Q, R;
	private EigenvalueDecomposition Qdecomp, Rdecomp;

	public MigrationModel() { }

	@Override
	public void initAndValidate() throws Exception {
		updateMatrices();
	}
	
	public void updateMatrices() throws Exception {
		rateMatrix = rateMatrixInput.get();
		popSizes = popSizesInput.get();

		totalPopSize = 0.0;
		for (int i=0; i<popSizes.getDimension(); i++) {
			totalPopSize += popSizes.getArrayValue(i);
		}
		
		if (rateMatrix.getMinorDimension1() != rateMatrix.getMinorDimension2())
			throw new Exception("Migration matrix must be square!");
		
		if (rateMatrix.getMinorDimension1() != popSizes.getDimension())
			throw new Exception("Side of migration matrix not equal length of"
					+ " population size vector.");

		int nColours = getNDemes();

		mu = 0.0;
		Q = new DenseDoubleMatrix2D(nColours, nColours);
		
		// Set up backward transition rate matrix:
		for (int i=0; i<nColours; i++) {
			Q.set(i, i, 0.0);
			for (int j=0; j<nColours; j++) {
				if (i != j) {
					Q.set(j,i, getBackwardRate(j, i));
					Q.set(i, i, Q.get(i, i)-Q.get(j, i));
				}
			}

			if (Q.get(i,i)>mu)
				mu = Q.get(i,i);
		}
		
		// Set up uniformised backward transition rate matrix:
		R = new DenseDoubleMatrix2D(nColours, nColours);
		for (int i=0; i<nColours; i++) {
			for (int j=0; j<nColours; j++) {
				R.set(j, i, Q.get(j, i)/mu);
				if (j==i)
					R.set(j, i, R.get(j,i)+1.0);
			}
		}
		
		// Construct eigenvalue decompositions:
		Qdecomp = new EigenvalueDecomposition(Q);
		Rdecomp = new EigenvalueDecomposition(R);
	}

	/**
	 * Obtain the number of demes in the migration model.
	 * 
	 * @return Length of side of migration matrix.
	 */
	public int getNDemes() {
		return rateMatrix.getMinorDimension1();
	}

	/**
	 * Obtain element of rate matrix for migration model.
	 * 
	 * @return Rate matrix element.
	 */
	public double getRate(int i, int j) {
		return rateMatrix.getMatrixValue(i, j);
	}

	/**
	 * Obtain element of backward-time migration matrix corresponding to
	 * the backward-time transition rate from deme j to deme i.
	 * 
	 * @param i Destination deme
	 * @param j Source deme
	 * @return Rate matrix element.
	 */
	public double getBackwardRate(int i, int j) {
		return rateMatrix.getMatrixValue(j, i)
				*popSizes.getArrayValue(i)
				/popSizes.getArrayValue(j);
	}

	/**
	 * Obtain effective population size of particular colour.
	 * 
	 * @param i colour index
	 * @return Effective population size.
	 */
	public double getPopSize(int i) {
		return popSizes.getArrayValue(i);
	}

	/**
	 * Obtain total effective population size across all demes.
	 * 
	 * @return Effective population size.
	 */
	public double getTotalPopSize() {
		return totalPopSize;
	}
	
	/**
	 * Return element (i,j) of the A^power, where A is the matrix represented
	 * by the eigenvalue decomposition ed.  Assumes real eigenvectors.
	 * 
	 * @param ed
	 * @param power
	 * @param i
	 * @param j
	 * @return [A^power]_ij
	 */
	private double matrixPower(EigenvalueDecomposition ed,
			double power, int i, int j) {
		double result = 0.0;

		DoubleMatrix2D V = ed.getV();
		DoubleMatrix1D lambda = ed.getRealEigenvalues();
		
		for (int k=0; k<lambda.size(); k++)
			result += V.get(i, k)*V.get(j,k)*Math.pow(lambda.get(k), power);

		return result;
	}
	
	/**
	 * Return element (i,j) of exp(factor*A), where A is the matrix represented
	 * by the eigenvalue decomposition ed. Assumes real eigenvectors.
	 * 
	 * @param ed
	 * @param factor
	 * @param i
	 * @param j
	 * @return [exp(A)]_ij
	 */
	private double matrixExp(EigenvalueDecomposition ed,
			double factor, int i, int j) {
		double result = 0.0;
		
		DoubleMatrix2D V = ed.getV();
		DoubleMatrix1D lambda = ed.getRealEigenvalues();
		
		for (int k=0; k<lambda.size(); k++)
			result += V.get(i,k)*V.get(j, k)*Math.exp(factor*lambda.get(k));
		
		return result;
	}
	
	public double getMu() {
		return mu;
	}
	
	public double getQelement(int i, int j) {
		return Q.get(i, j);
	}
	
	public double getRelement(int i, int j) {
		return R.get(i, j);
	}
	
	public double getQpower(double power, int i, int j) {
		return matrixPower(Qdecomp, power, i, j);
	}
	
	public double getRpower(double power, int i, int j) {
		return matrixPower(Rdecomp, power, i, j);
	}
	
	public double getQexp(double factor, int i, int j) {
		return matrixExp(Qdecomp, factor, i, j);
	}

}

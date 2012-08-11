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
import cern.colt.function.DoubleFunction;
import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.colt.matrix.linalg.Blas;
import cern.colt.matrix.linalg.SeqBlas;
import cern.jet.math.Functions;
import java.util.ArrayList;
import java.util.List;

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
	private List<DoubleMatrix2D> Rpowers;

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

			if (-Q.get(i,i)>mu)
				mu = -Q.get(i,i);
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

		// Clear cache for powers of R:
		Rpowers = new ArrayList<DoubleMatrix2D>();
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
		
	public double getMu() {
		return mu;
	}
	
	public double getQelement(int i, int j) {
		return Q.get(i, j);
	}
	
	public double getRelement(int i, int j) {
		return R.get(i, j);
	}
	
	/**
	 * Return A^power.  Implements a caching mechanism.
	 * 
	 * @param power
	 * @return A^power
	 */
	public DoubleMatrix2D getRpower(int power) {

		if (power>Rpowers.size()-1) {
			if (power>10000) {
				System.out.println(power);
			}
			for (int n=Rpowers.size()-1; n<power; n++) {
				if (n<0)
					Rpowers.add(DoubleFactory2D.dense.identity(getNDemes()));
				else
					Rpowers.add(Algebra.DEFAULT.mult(Rpowers.get(n), R));
			}
		}
		
		return Rpowers.get(power);
	}
	
	/**
	 * Return element (i,j) of R^power.
	 * 
	 * @param power
	 * @param i
	 * @param j
	 * @return [R^power]_ij
	 */
	public double getRpowerElement(int power, int i, int j) {
		return getRpower(power).get(i,j);
	}
	
	/**
	 * Calculate exponential of the product factor*R, truncating at term trunc.
	 * 
	 * @param factor
	 * @param trunc
	 * @return exp(factor*R)
	 */
	public DoubleMatrix2D getRexp(double factor, int trunc) {
		
		DoubleMatrix2D res = DoubleFactory2D.dense.identity(getNDemes());
		double scalar = 1.0;
		
		for (int n=1; n<trunc; n++) {
			DoubleMatrix2D Rn = getRpower(n).copy();
			scalar *= factor/n;
			Rn.assign(Functions.mult(scalar));
			res.assign(Rn, Functions.plus);
		}
		
		return res;
	}
	
	/**
	 * Obtain element (i,j) of exp(factor*R), truncating at term trunc.
	 * 
	 * @param factor
	 * @param trunc
	 * @param i
	 * @param j
	 * @return [exp(factor*R)]_ij
	 */
	public double getRexpElement(double factor, int trunc, int i, int j) {
		return getRexp(factor, trunc).get(i, j);
	}
	
	public static void main (String[] args) throws Exception {
		
		RealParameter pops = new RealParameter();
		pops.initByName(
				"dimension", 2,
				"value", "7.0 7.0");
		RealParameter migmatrix = new RealParameter();
		migmatrix.initByName(
				"dimension", 4,
				"minordimension", 2,
				"value", "0.0 1 2 0.0");
		MigrationModel mig = new MigrationModel();
		mig.initByName(
				"popSizes", pops,
				"rateMatrix", migmatrix);
		
		System.out.println("Q=" + mig.Q);
		System.out.println("mu=" + mig.mu);
		System.out.println("R=" + mig.R);
		
		System.out.println("R^3=" + mig.getRpower(3));
		
		System.out.println("exp(R)=" + mig.getRexp(1.0, 10));
	}
}

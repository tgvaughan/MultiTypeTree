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

import beast.core.CalculationNode;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Plugin;
import beast.core.parameter.RealParameter;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.colt.matrix.linalg.EigenvalueDecomposition;

import java.util.Arrays;

/**
 * Basic plugin describing a simple Markovian migration model, for use by
 * ColouredTree operators and likelihoods. Note that this class and package are
 * just stubs. We expect to have something similar to the SubstitutionModel
 * class/interface eventually.
 *
 * @author Tim Vaughan
 */
@Description("Basic plugin describing a simple Markovian migration model.")
public class MigrationModel extends CalculationNode {

    public Input<RealParameter> rateMatrixInput = new Input<RealParameter>(
            "rateMatrix",
            "Migration rate matrix",
            Validate.REQUIRED);
    public Input<RealParameter> popSizesInput = new Input<RealParameter>(
            "popSizes",
            "Deme population sizes.",
            Validate.REQUIRED);
    
    private RealParameter rateMatrix, popSizes;
    private double totalPopSize;
    private double mu;
    private DoubleMatrix2D Q, R;
    private EigenvalueDecomposition Qdecomp, Rdecomp;
    private DoubleMatrix2D QVinv, RVinv;

    public MigrationModel() { }

    @Override
    public void initAndValidate() throws Exception {
        updateMatrices();
    }

    public void updateMatrices() throws Exception {

        popSizes = popSizesInput.get();

        if (rateMatrixInput.get().getDimension() == popSizes.getDimension() * popSizes.getDimension())
            rateMatrix = rateMatrixInput.get();
        else if (rateMatrixInput.get().getDimension() == popSizes.getDimension() * (popSizes.getDimension() - 1))
            addDiagonal(rateMatrixInput.get().getValues(), popSizes.getDimension());


        totalPopSize = 0.0;
        for (int i = 0; i < popSizes.getDimension(); i++)
            totalPopSize += popSizes.getArrayValue(i);

        if (rateMatrix.getMinorDimension1() != rateMatrix.getMinorDimension2())
            throw new Exception("Migration matrix must be square!");

        if (rateMatrix.getMinorDimension1() != popSizes.getDimension())
            throw new Exception("Side of migration matrix not equal length of"
                    + " population size vector.");

        int nColours = getNDemes();

        mu = 0.0;
        Q = new DenseDoubleMatrix2D(nColours, nColours);

        // Set up backward transition rate matrix:
        for (int i = 0; i < nColours; i++) {
            Q.set(i, i, 0.0);
            for (int j = 0; j < nColours; j++)
                if (i != j) {
                    Q.set(j, i, getBackwardRate(j, i));
                    Q.set(i, i, Q.get(i, i) - Q.get(j, i));
                }

            if (-Q.get(i, i) > mu)
                mu = -Q.get(i, i);
        }

        // Set up uniformised backward transition rate matrix:
        R = new DenseDoubleMatrix2D(nColours, nColours);
        for (int i = 0; i < nColours; i++)
            for (int j = 0; j < nColours; j++) {
                R.set(j, i, Q.get(j, i) / mu);
                if (j == i)
                    R.set(j, i, R.get(j, i) + 1.0);
            }

        // Calculate eigenvalue decomps:
        Qdecomp = new EigenvalueDecomposition(Q);
        Rdecomp = new EigenvalueDecomposition(R);
        QVinv = Algebra.DEFAULT.inverse(Qdecomp.getV());
        RVinv = Algebra.DEFAULT.inverse(Rdecomp.getV());

    }

    /**
     * Add zeros to the diagonal -
     *
     * @Tim: I think we should parametrize the rate matrix as a n*(n-1) matrix
     * @Denise: Noooo! :-P
     *
     * @param dim*(dim-1) rate matrix
     * @author Denise
     */
    void addDiagonal(Double[] matrix, int dim) throws Exception {

        Double[] squareMatrix = new Double[dim * dim];

        int count = 0;
        for (int i = 0; i < dim; i++)
            for (int j = 0; j < dim; j++)
                if (i == j)
                    squareMatrix[i * dim + j] = 0.;
                else {
                    squareMatrix[i * dim + j] = matrix[count];
                    count++;
                }

        rateMatrix = new RealParameter();
        rateMatrix.initByName("value", Arrays.toString(squareMatrix).replaceAll("[\\[\\],]", " "),
                "minordimension", 2);
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
     * Obtain element of backward-time migration matrix corresponding to the
     * backward-time transition rate from deme j to deme i.
     *
     * @param i Destination deme
     * @param j Source deme
     * @return Rate matrix element.
     */
    public double getBackwardRate(int i, int j) {
        return rateMatrix.getMatrixValue(j, i)
                * popSizes.getArrayValue(i)
                / popSizes.getArrayValue(j);
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
     * Calculate exponential of the product factor*A, where A is the matrix with
     * eigendecomp decomp and eigenvector matrix inverse Vinv.
     *
     * @param decomp
     * @param Vinv
     * @param factor
     * @return exp(factor*A)
     */
    public DoubleMatrix2D getMatrixExp(EigenvalueDecomposition decomp,
            DoubleMatrix2D Vinv, double factor) {
        DoubleMatrix2D V = decomp.getV();
        DoubleMatrix2D D = decomp.getD().copy();

        for (int i = 0; i < D.rows(); i++)
            D.set(i, i, Math.exp(factor * D.get(i, i)));

        // V*exp(factor*D)*Vinv:
        return Algebra.DEFAULT.mult(Algebra.DEFAULT.mult(V, D), Vinv);
    }

    public DoubleMatrix2D getMatrixPow(EigenvalueDecomposition decomp,
            DoubleMatrix2D Vinv, double power, double factor) {
        DoubleMatrix2D V = decomp.getV();
        DoubleMatrix2D D = decomp.getD().copy();

        for (int i = 0; i < D.rows(); i++)
            D.set(i, i, Math.pow(factor * D.get(i, i), power));

        return Algebra.DEFAULT.mult(Algebra.DEFAULT.mult(V, D), Vinv);
    }

    /**
     * Calculate exp(factor*Q)
     *
     * @param factor
     * @return exp(factor*Q)
     */
    public DoubleMatrix2D getQexp(double factor) {
        return getMatrixExp(Qdecomp, QVinv, factor);
    }

    /**
     * Calculate (factor*Q)^power
     *
     * @param power
     * @param factor
     * @return (factor*Q)^power
     */
    public DoubleMatrix2D getQpow(double power, double factor) {
        return getMatrixPow(Qdecomp, QVinv, power, factor);
    }

    /**
     * Obtain element (i,j) of exp(factor*Q).
     *
     * @param factor
     * @param i
     * @param j
     * @return [exp(factor*Q)]_ij
     */
    public double getQexpElement(double factor, int i, int j) {
        return getQexp(factor).get(i, j);
    }

    /**
     * Obtain element (i,j) of (factor*Q)^power.
     *
     * @param power
     * @param factor
     * @param i
     * @param j
     * @return [(factor*Q)^power]_ij
     */
    public double getQpowElement(double power, double factor, int i, int j) {
        return getQpow(power, factor).get(i, j);
    }

    /**
     * Calculate exp(factor*R).
     *
     * @param factor
     * @return exp(factor*R)
     */
    public DoubleMatrix2D getRexp(double factor) {
        return getMatrixExp(Rdecomp, RVinv, factor);
    }

    /**
     * Calculate (factor*R)^power.
     *
     * @param power
     * @param factor
     * @return (factor*R)^power
     */
    public DoubleMatrix2D getRpow(double power, double factor) {
        return getMatrixPow(Rdecomp, RVinv, power, factor);
    }

    /**
     * Calculate element (i,j) of exp(factor*R).
     *
     * @param factor
     * @param i
     * @param j
     * @return [exp(factor*R)_ij
     */
    public double getRexpElement(double factor, int i, int j) {
        return getRexp(factor).get(i, j);
    }

    /**
     * Calculate element (i,j) of (factor*R)^power.
     *
     * @param power
     * @param factor
     * @param i
     * @param j
     * @return [(factor*R)^power]_ij
     */
    public double getRpowElement(double power, double factor, int i, int j) {
        return getRpow(power, factor).get(i, j);
    }
    
    /**
     * CalculationNode implementations *
     */
    @Override
    protected boolean requiresRecalculation() {
        // we only get here if something is dirty
        updateMatrix = true;
        return true;
    }

    @Override
    protected void restore() {
        updateMatrix = true;
        super.restore();
    }

    /**
     * Main method for debugging only.
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

//		RealParameter pops = new RealParameter();
//		pops.initByName(
//				"dimension", 2,
//				"value", "7.0 7.0");
//		RealParameter migmatrix = new RealParameter();
//		migmatrix.initByName(
//				"dimension", 4,
//				"minordimension", 2,
//				"value", "0.0 1 2 0.0");
//		MigrationModel mig = new MigrationModel();
//		mig.initByName(
//				"popSizes", pops,
//				"rateMatrix", migmatrix);
//
//		System.out.println("Q=" + mig.Q);
//		System.out.println("mu=" + mig.mu);
//		System.out.println("R=" + mig.R);
//
//		System.out.println("Q^0=" + mig.getQpow(0.0, 1.0));

        MigrationModel mig = new MigrationModel();
        Double[] matrix = new Double[]{1., 2., 3., 4., 5., 6., 1., 2., 3., 4., 5., 6.};
        int dim = 4;
        mig.addDiagonal(matrix, dim);

    }
}

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
import beast.core.parameter.RealParameter;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.colt.matrix.linalg.EigenvalueDecomposition;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Basic plugin describing a simple Markovian migration model, for use by
 * ColouredTree operators and likelihoods. Note that this class and package are
 * just stubs. We expect to have something similar to the SubstitutionModel
 * class/interface eventually.
 * 
 * Note that the transition rate matrices exposed for the uniformization
 * recolouring operators are symmetrized variants of the actual rate
 * matrices, to allow for easier diagonalization.
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
    
    public Input<Boolean> rateMatrixIsBackward = new Input<Boolean>(
            "rateMatrixIsBackward",
            "If true, rate matrix elements are read as reverse-time rates.  "
            + "Default true.",
            true);
    
    public Input<Double> uniformInitialRateInput = new Input<Double>(
            "uniformInitialRate",
            "Specify uniform rate with which to initialise matrix.  "
            + "Overrides previous dimension and value of matrix.");
    
    private RealParameter rateMatrix, popSizes;
    private double totalPopSize;
    private double mu;
    private int nTypes;
    private DoubleMatrix2D Q, R;
    private EigenvalueDecomposition Qdecomp, Rdecomp;
    private DoubleMatrix2D QVinv, RVinv;
    
    private boolean rateMatrixIsSquare;
    
    // Flag to indicate whether EV decompositions need updating.
    private boolean dirty;

    public MigrationModel() { }

    @Override
    public void initAndValidate() throws Exception {
        
        popSizes = popSizesInput.get();
        nTypes = popSizes.getDimension();
        rateMatrix = rateMatrixInput.get();
        
        if (uniformInitialRateInput.get() != null) {
            
            double rate = uniformInitialRateInput.get();
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<nTypes; i++) {
                for (int j=0; j<nTypes; j++) {
                    if (i==j)
                        continue;
                    
                    sb.append(String.valueOf(rate)).append(" ");
                }
            }
            rateMatrixInput.get().initByName("value", sb.toString());
        }
        
        if (rateMatrix.getDimension() == nTypes*nTypes)
            rateMatrixIsSquare = true;
        else {
            if (rateMatrix.getDimension() != nTypes*(nTypes-1)) {
                throw new IllegalArgumentException("Migration matrix has"
                        + "incorrect number of elements for given deme count.");
            } else
                rateMatrixIsSquare = false;
        }
        
        dirty = true;
        updateMatrices();
    }

    /**
     * Ensure all local fields including matrices and eigenvalue decomposition
     * objects are consistent with current values held by inputs.
     */
    public void updateMatrices()  {
        
        if (!dirty)
            return;

        popSizes = popSizesInput.get();
        rateMatrix = rateMatrixInput.get();

        totalPopSize = 0.0;
        for (int i = 0; i < popSizes.getDimension(); i++)
            totalPopSize += popSizes.getArrayValue(i);

        mu = 0.0;
        Q = new DenseDoubleMatrix2D(nTypes, nTypes);

        // Set up _symmetrized_ backward transition rate matrix:
        for (int i = 0; i < nTypes; i++) {
            Q.set(i, i, 0.0);
            for (int j = 0; j < nTypes; j++)
                if (i != j) {
                    Q.set(j, i, 0.5*(getBackwardRate(j, i) + getBackwardRate(i,j)));
                    Q.set(i, i, Q.get(i, i) - Q.get(j, i));
                }

            if (-Q.get(i, i) > mu)
                mu = -Q.get(i, i);
        }

        // Set up uniformised backward transition rate matrix:
        R = new DenseDoubleMatrix2D(nTypes, nTypes);
        for (int i = 0; i < nTypes; i++)
            for (int j = 0; j < nTypes; j++) {
                R.set(j, i, Q.get(j, i) / mu);
                if (j == i)
                    R.set(j, i, R.get(j, i) + 1.0);
            }

        // Calculate eigenvalue decomps:
        Qdecomp = new EigenvalueDecomposition(Q);
        Rdecomp = new EigenvalueDecomposition(R);
        QVinv = Algebra.DEFAULT.inverse(Qdecomp.getV());
        RVinv = Algebra.DEFAULT.inverse(Rdecomp.getV());
        
        dirty = false;

    }

    /**
     * @return number of demes in the migration model.
     */
    public int getNDemes() {
        return nTypes;
    }

    /**
     * Obtain element of rate matrix for migration model.
     *
     * @return Rate matrix element.
     */
    public double getRate(int i, int j) {
        if (i==j)
            return 0;
        
        if (rateMatrixIsSquare) {
            return rateMatrix.getValue(i*nTypes+j);            
        } else {
            if (j>i)
                j -= 1;
            return rateMatrix.getValue(i*(nTypes-1)+j);            
        }



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
        if (rateMatrixIsBackward.get())
            return getRate(i, j);
        else 
            return getRate(j, i)
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
        updateMatrices();
        return totalPopSize;
    }

    public double getMu() {
        updateMatrices();
        return mu;
    }

    public double getQelement(int i, int j) {
        updateMatrices();
        return Q.get(i, j);
    }

    public double getRelement(int i, int j) {
        updateMatrices();
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
        updateMatrices();
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
        updateMatrices();
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
        updateMatrices();
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
        updateMatrices();
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
        dirty = true;
        return true;
    }

    @Override
    protected void restore() {
        dirty = true;
        super.restore();
    }

    /**
     * Main method for debugging only.
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) {


    }
}

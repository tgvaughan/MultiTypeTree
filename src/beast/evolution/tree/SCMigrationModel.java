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
package beast.evolution.tree;

import beast.core.CalculationNode;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.parameter.BooleanParameter;
import beast.core.parameter.RealParameter;

import java.util.ArrayList;
import java.util.List;
import org.jblas.DoubleMatrix;
import org.jblas.MatrixFunctions;

/**
 * @author Tim Vaughan
 */
@Description("Basic plugin describing a simple Markovian migration model.")
public class SCMigrationModel extends CalculationNode implements MigrationModel {

    public Input<RealParameter> rateMatrixInput = new Input<>(
            "rateMatrix",
            "Migration rate matrix",
            Validate.REQUIRED);
    public Input<RealParameter> popSizesInput = new Input<>(
            "popSizes",
            "Deme population sizes.",
            Validate.REQUIRED);
    
    public Input<BooleanParameter> rateMatrixFlagsInput = new Input<>(
            "rateMatrixFlags",
            "Optional boolean parameter specifying which rates to use."
            + " (Default is to use all rates.)");
    
    protected RealParameter rateMatrix, popSizes;
    protected BooleanParameter rateMatrixFlags;
    protected double mu, muSym;
    protected int nTypes;
    protected DoubleMatrix Q, R;
    protected DoubleMatrix Qsym, Rsym;
    protected List<DoubleMatrix> RpowN, RsymPowN;
    protected DoubleMatrix RpowMax, RsymPowMax;
    protected boolean RpowSteady, RsymPowSteady;
    
    protected boolean rateMatrixIsSquare, symmetricRateMatrix;
    
    // Flag to indicate whether EV decompositions need updating.
    protected boolean dirty;

    public SCMigrationModel() {
        // Initialise caching array for powers of uniformized
        // transition matrix:
        RpowN = new ArrayList<>();
        RsymPowN = new ArrayList<>();
    }

    @Override
    public void initAndValidate() throws Exception {
        
        popSizes = popSizesInput.get();
        rateMatrix = rateMatrixInput.get();
        nTypes = popSizes.getDimension();

        if (rateMatrixFlagsInput.get() != null)
            rateMatrixFlags = rateMatrixFlagsInput.get();

        rateMatrix.setLower(Math.max(rateMatrix.getLower(), 0.0));
        popSizes.setLower(Math.max(popSizes.getLower(), 0.0));
        
        if (rateMatrix.getDimension() == nTypes*nTypes) {
            rateMatrixIsSquare = true;
            symmetricRateMatrix = false;
        } else {
            if (rateMatrix.getDimension() != nTypes*(nTypes-1)) {
                if (rateMatrix.getDimension() == nTypes*(nTypes-1)/2) {
                    symmetricRateMatrix = true;
                } else 
                    throw new IllegalArgumentException("Migration matrix has "
                            + "incorrect number of elements for given deme count.");
            } else {
                rateMatrixIsSquare = false;
                symmetricRateMatrix = false;
            }
        }
        
        if (rateMatrixFlags != null) {
            if (rateMatrixFlags.getDimension() != rateMatrix.getDimension())
                throw new IllegalArgumentException("Migration rate flags"
                        + " array does not have same number of elements as"
                        + " migration rate matrix.");
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

        mu = 0.0;
        muSym = 0.0;
        Q = new DoubleMatrix(nTypes, nTypes);
        Qsym = new DoubleMatrix(nTypes, nTypes);

        // Set up backward transition rate matrix Q and symmetrized backward
        // transition rate matrix Qsym:
        for (int i = 0; i < nTypes; i++) {
            Q.put(i,i, 0.0);
            Qsym.put(i,i, 0.0);
            for (int j = 0; j < nTypes; j++) {
                if (i != j) {
                    Q.put(i, j, getBackwardRate(i, j));
                    Q.put(i, i, Q.get(i, i) - Q.get(i, j));
                    
                    Qsym.put(i, j, 0.5*(getBackwardRate(i, j) + getBackwardRate(j, i)));
                    Qsym.put(i, i, Qsym.get(i, i) - Qsym.get(i, j));
                }
            }

            if (-Q.get(i, i) > mu)
                mu = -Q.get(i, i);
            
            if (-Qsym.get(i,i) > muSym)
                muSym = -Qsym.get(i, i);
        }

        // Set up uniformized backward transition rate matrices R and Rsym:
        R = Q.mul(1.0/mu).add(DoubleMatrix.eye(nTypes));
        Rsym = Qsym.mul(1.0/muSym).add(DoubleMatrix.eye(nTypes));
        
        // Clear cached powers of R and Rsym and steady state flag:
        RpowN.clear();
        RsymPowN.clear();
        
        RpowSteady = false;
        RsymPowSteady = false;
        
        // Power sequences initially contain R^0 = I
        RpowN.add(DoubleMatrix.eye(nTypes));
        RsymPowN.add(DoubleMatrix.eye(nTypes));
        
        RpowMax = DoubleMatrix.eye(nTypes);
        RsymPowMax = DoubleMatrix.eye(nTypes);

        dirty = false;
    }

    /**
     * @return number of demes in the migration model.
     */
    @Override
    public int getNTypes() {
        return nTypes;
    }
    
    /**
     * Obtain element of rate matrix for migration model.  Unlike getRate(),
     * this method does not return 0 when the BSSVS indicator variable is
     * switched off.
     *
     * @param i
     * @param j
     * @return Rate matrix element.
     */
    public double getRateForLog(int i, int j) {
        
        if (i==j)
            return 0;

        return rateMatrix.getValue(getArrayOffset(i, j));
        
    }

    /**
     * Obtain element of rate matrix for migration model for use in likelihood
     * calculation.  (May be switched to zero in BSSVS calculation.)
     *
     * @param i
     * @param j
     * @return Rate matrix element.
     */
    @Override
    public double getBackwardRate(int i, int j) {
        if (i==j)
            return 0;
        
        int offset = getArrayOffset(i, j);
        if (rateMatrixFlagsInput.get() != null
                && !rateMatrixFlagsInput.get().getValue(offset))
            return 0.0;
        else
            return rateMatrix.getValue(offset);
    }
    
    /**
     * Obtain BSSVS flag corresponding to element of rate matrix for migration
     * model.  If flags are not being used, returns true.
     *
     * @param i
     * @param j
     * @return Rate matrix element BSVS flag.
     */
    public boolean getRateFlag(int i, int j) {
         
        if (i==j)
            return false;
        
        if (rateMatrixFlagsInput.get() == null)
           return true;

        return rateMatrixFlagsInput.get().getValue(getArrayOffset(i, j));
    }

    /**
     * Retrieve rate of migration from i to j forward in time.
     *
     * @param i source deme
     * @param j dest deme
     * @return migration rate
     */
    @Override
    public double getForwardRate(int i, int j) {
        if (i==j)
            return 0.0;

        return getBackwardRate(j, i)*getPopSize(j)/getPopSize(i);
    }
    
    /**
     * Set element of rate matrix for migration model.
     * This method should only be called by operators.
     * 
     * @param i
     * @param j
     * @param rate 
     */
    public void setBackwardRate(int i, int j, double rate) {
        if (i==j)
            return;
        
        rateMatrix.setValue(getArrayOffset(i,j), rate);

        // Model is now dirty.
        dirty = true;
    }

    /**
     * Obtain offset into "rate matrix" and associated flag arrays.
     * 
     * @param i
     * @param j
     * @return Offset (or -1 if i==j)
     */
    protected int getArrayOffset(int i, int j) {
        
        if (i==j)
            throw new RuntimeException("Programmer error: requested migration "
                    + "rate array offset for diagonal element of "
                    + "migration rate matrix.");
        
        if (rateMatrixIsSquare) {
            return i*nTypes+j;
        } else {
            if (symmetricRateMatrix) {
             if (j<i)
                 return i*(i-1)/2 + j;
             else
                 return j*(j-1)/2 + i;
            } else {
                if (j>i)
                    j -= 1;
                return i*(nTypes-1)+j;
            }
        }
    }
    
    /**
     * Obtain effective population size of particular type/deme.
     *
     * @param i deme index
     * @return Effective population size.
     */
    public double getPopSize(int i) {
        return popSizes.getArrayValue(i);
    }
    
    /**
     * Set effective population size of particular type/deme.
     * 
     * @param i deme index
     * @param newSize 
     */
    public void setPopSize(int i, double newSize) {
        popSizes.setValue(i, newSize);
        dirty = true;
    }

    @Override
    public double getMu(boolean symmetric) {
        updateMatrices();
        if (symmetric)
            return muSym;
        else
            return mu;
    }
    
    @Override
    public DoubleMatrix getR(boolean symmetric) {
        updateMatrices();
        if (symmetric)
            return Rsym;
        else
            return R;
    }
    
    @Override
    public DoubleMatrix getQ(boolean symmetric) {
        updateMatrices();
        if (symmetric)
            return Qsym;
        else
            return Q;
    }
    
    @Override
    public DoubleMatrix getRpowN(int n, boolean symmetric) {
        updateMatrices();
        
        List <DoubleMatrix> matPowerList;
        DoubleMatrix mat, matPowerMax;
        if (symmetric) {
            matPowerList = RsymPowN;
            mat = Rsym;
            matPowerMax = RsymPowMax;
        } else {
            matPowerList = RpowN;
            mat = R;
            matPowerMax = RpowMax;
        }
        
        if (n>=matPowerList.size()) {
                
            // Steady state of matrix iteration already reached
            if ((symmetric && RsymPowSteady) || (!symmetric && RpowSteady)) {
                //System.out.println("Assuming R SS.");
                return matPowerList.get(matPowerList.size()-1);
            }
                
            int startN = matPowerList.size();
            for (int i=startN; i<=n; i++) {
                matPowerList.add(matPowerList.get(i-1).mmul(mat));
                
                matPowerMax.maxi(matPowerList.get(i));
                    
                // Occasionally check whether matrix iteration has reached steady state
                if (i%10 == 0) {
                    double maxDiff = 0.0;
                    for (double el : matPowerList.get(i).sub(matPowerList.get(i-1)).toArray())
                        maxDiff = Math.max(maxDiff, Math.abs(el));
                        
                    if (!(maxDiff>0)) {
                        if (symmetric)
                            RsymPowSteady = true;
                        else
                            RpowSteady = true;
                        
                        return matPowerList.get(i);
                    }
                }
            }
        }
        return matPowerList.get(n);
    }

    /**
     * Power above which R is known to be steady.
     * 
     * @param symmetric
     * @return index of first known steady element.
     */
    @Override
    public int RpowSteadyN(boolean symmetric) {
        if (symmetric) {
            if (RsymPowSteady)
                return RsymPowN.size();
            else
                return -1;
        } else {
            if (RpowSteady)
                return RpowN.size();
            else
                return -1;
        }
    }

    /*
     * CalculationNode implementations.
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
     * Main for debugging.
     *
     * @param args
     */
    public static void main (String [] args) {
        
        int n=10;
        DoubleMatrix Q = new DoubleMatrix(n, n);
        for (int i=0; i<n; i++) {
            for (int j=0; j<n; j++) {
                Q.put(i, j, i*n+j);
            }
        }
        MatrixFunctions.expm(Q.mul(0.001)).print();
        Q.print();
        
    }
}

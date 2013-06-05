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
package beast.evolution.operators;

import beast.core.Input;
import beast.core.State;
import beast.core.parameter.RealParameter;
import beast.evolution.migrationmodel.MigrationModel;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.MultiTypeTreeFromNewick;
import beast.evolution.tree.Node;
import beast.util.Randomizer;
import java.io.PrintStream;
import java.util.Arrays;
import org.jblas.DoubleMatrix;
import org.jblas.MatrixFunctions;

/**
 * Abstract class of operators on MultiTypeTrees which use the Fearnhead-Sherlock
 * uniformization/forward-backward algorithm for branch retyping.
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public abstract class UniformizationRetypeOperator extends MultiTypeTreeOperator {
    
    public Input<MigrationModel> migrationModelInput = new Input<MigrationModel>(
            "migrationModel",
            "Migration model for proposal distribution", Input.Validate.REQUIRED);
    
    public Input<Integer> rejectThresholdInput = new Input<Integer>(
            "rejectThreshold",
            "Expected number of changes on branch above which safer but slower"
            + " rejection sampling is used. (Default 50.)", 50);
    
    public Input<Boolean> useSymmetrizedRatesInput = new Input<Boolean>(
            "useSymmetrizedRates",
            "Use symmetrized rate matrix to propose migration paths.", true);
    
    
    /**
     * Sample the number of virtual events to occur along branch.
     * @param typeStart Type at start (bottom) of branch
     * @param typeEnd Type at end (top) of branch
     * @param muL Expected unconditioned number of virtual events
     * @param Pba Probability of final type given start type
     * @param migrationModel Migration model to use.
     * @return number of virtual events.
     */
    private int drawEventCount(int typeStart, int typeEnd, double muL, double Pba,
            MigrationModel migrationModel, boolean sym) {
        
        int nVirt;

        if (muL>rejectThresholdInput.get()) {
            // Rejection sample for large mu*L
            // (Avoids numerical difficulties produced by direct method.)
            
            double P_b_given_na;

            do {
                nVirt = (int) Randomizer.nextPoisson(muL);

                P_b_given_na = migrationModel.getRpowN(nVirt, sym).get(typeStart, typeEnd);
                
            } while (Randomizer.nextDouble()>P_b_given_na);
            
        } else {
            // Direct sampling for smaller mu*L
            
            nVirt = 0;
            double u = Randomizer.nextDouble()*Pba;
            double poisAcc = Math.exp(-muL);
            u -= poisAcc*migrationModel.getRpowN(0, sym).get(typeStart, typeEnd);
            while (u>0.0) {
                nVirt += 1;
                
                if (nVirt>1e6) {
                    System.err.println("WARNING: direct P(n|a,b) sampler in "
                            + "UniformizationRetypeOperator has exceeded 1e6 "
                            + "iterations.  This should never happen! "
                            + "Rejecting move.");
                    return -1;
                }
                
                poisAcc *= muL/nVirt;
                u -= poisAcc*migrationModel.getRpowN(nVirt,sym).get(typeStart, typeEnd);
            }
        }
        return nVirt;
    }
        
    /**
     * Retype branch between srcNode and its parent.  Uses the combined
     * uniformization/forward-backward approach of Fearnhead and Sherlock (2006)
     * to condition on both the beginning and end states.
     *
     * @param srcNode
     * @return Probability of new state.
     */
    protected double retypeBranch(Node srcNode) {
        
        boolean sym = useSymmetrizedRatesInput.get();
        
        MigrationModel migrationModel = migrationModelInput.get();

        Node srcNodeP = srcNode.getParent();
        double t_srcNode = srcNode.getHeight();
        double t_srcNodeP = srcNodeP.getHeight();

        double L = t_srcNodeP-t_srcNode;

        int type_srcNode = ((MultiTypeNode)srcNode).getNodeType();
        int type_srcNodeP = ((MultiTypeNode)srcNodeP).getNodeType();

        // Pre-calculate some stuff:
        double Pba = MatrixFunctions.expm(
                migrationModel.getQ(sym)
                .mul(L)).get(type_srcNode,type_srcNodeP);
        
        // Catch for numerical errors
        if (Pba>1.0 || Pba<0.0)
            return Double.NEGATIVE_INFINITY;
            
        
        double muL = migrationModel.getMu(sym)*L;
        
        // Select number of virtual events:
        int nVirt = drawEventCount(type_srcNode, type_srcNodeP, muL, Pba,
                migrationModel, sym);
        
        if (nVirt<0)
            return Double.NEGATIVE_INFINITY;
        
        // Select times of virtual events:
        double[] times = new double[nVirt];
        for (int i = 0; i<nVirt; i++)
            times[i] = Randomizer.nextDouble()*L+t_srcNode;
        Arrays.sort(times);

        // Sample type changes along branch using FB algorithm:
        int[] types = new int[nVirt];
        int prevType = type_srcNode;
        
        for (int i = 1; i<=nVirt; i++) {
            
            double u2 = Randomizer.nextDouble()
                    *migrationModel.getRpowN(nVirt-i+1, sym).get(prevType, type_srcNodeP);
            int c;
            boolean fellThrough = true;
            for (c = 0; c<mtTree.getNTypes(); c++) {
                u2 -= migrationModel.getR(sym).get(prevType,c)
                        *migrationModel.getRpowN(nVirt-i, sym).get(c,type_srcNodeP);
                if (u2<0.0) {
                    fellThrough = false;
                    break;
                }
            }

            types[i-1] = c;
            prevType = c;
        }

        double logProb = 0.0;

        // Add non-virtual type changes to branch, calculating probability
        // of path conditional on start type:
        ((MultiTypeNode)srcNode).clearChanges();
        prevType = type_srcNode;
        double prevTime = t_srcNode;
        for (int i = 0; i<nVirt; i++) {

            if (types[i] != prevType) {

                // Add change to branch:
                ((MultiTypeNode)srcNode).addChange(types[i], times[i]);

                // Add probability contribution:
                logProb += migrationModel.getQ(sym).get(prevType, prevType)*(times[i]-prevTime)
                        +Math.log(migrationModel.getQ(sym).get(prevType, types[i]));

                prevType = types[i];
                prevTime = times[i];
            }
        }
        logProb += migrationModel.getQ(sym).get(prevType, prevType)*(t_srcNodeP-prevTime);

        // Adjust probability to account for end condition:
        logProb -= Math.log(Pba);

        // Return probability of path given boundary conditions:
        return logProb;
    }
    
    /**
     * Obtain probability of the current migratory path above srcNode.
     *
     * @param srcNode
     * @return Path probability.
     */
    protected double getBranchTypeProb(Node srcNode) {
        
        MigrationModel migrationModel = migrationModelInput.get();
        boolean sym = useSymmetrizedRatesInput.get();

        double logProb = 0.0;

        Node srcNodeP = srcNode.getParent();
        double t_srcNode = srcNode.getHeight();
        double t_srcNodeP = srcNodeP.getHeight();
        double L = t_srcNodeP-t_srcNode;
        int col_srcNode = ((MultiTypeNode)srcNode).getNodeType();
        int col_srcNodeP = ((MultiTypeNode)srcNodeP).getNodeType();

        // Probability of branch conditional on start type:
        double lastTime = t_srcNode;
        int lastCol = col_srcNode;
        for (int i = 0; i<((MultiTypeNode)srcNode).getChangeCount(); i++) {
            double thisTime = ((MultiTypeNode)srcNode).getChangeTime(i);
            int thisCol = ((MultiTypeNode)srcNode).getChangeType(i);

            logProb += (thisTime-lastTime)*migrationModel.getQ(sym).get(lastCol, lastCol)
                    +Math.log(migrationModel.getQ(sym).get(lastCol, thisCol));

            lastTime = thisTime;
            lastCol = thisCol;
        }
        logProb += (t_srcNodeP-lastTime)*migrationModel.getQ(sym).get(lastCol, lastCol);

        // Adjust to account for end condition of path:
        double Pba = MatrixFunctions.expm(
                migrationModel.getQ(sym).mul(L)).get(col_srcNode, col_srcNodeP);
        
        // Catch for numerical errors:
        if (Pba>1.0 || Pba < 0.0)
            return Double.NEGATIVE_INFINITY;
        
        logProb -= Math.log(Pba);
                
        return logProb;
    }


    /**
     * Main method for debugging.
     * 
     * @param args 
     */
    public static void main(String[] args) throws Exception {
       
        // Generate an ensemble of paths along a branch of a tree.
        
        // Assemble initial MultiTypeTree
        String newickStr =
                "((1[deme='0']:50)[deme='1']:150,2[deme='1']:200)[deme='1']:0;";
        
        MultiTypeTreeFromNewick mtTree = new MultiTypeTreeFromNewick();
        mtTree.initByName(
                "newick", newickStr,
                "typeLabel", "deme",
                "nTypes", 4);
        
        // Assemble migration model:
        RealParameter rateMatrix = new RealParameter(
                "0.20 0.02 0.03 0.04 "
                + "0.05 0.06 0.07 0.08 "
                + "0.09 0.10 0.11 0.12");
        RealParameter popSizes = new RealParameter("7.0 7.0 7.0 7.0");
        MigrationModel migModel = new MigrationModel();
        migModel.initByName(
                "rateMatrix", rateMatrix,
                "popSizes", popSizes);
        
        // Set up state:
        State state = new State();
        state.initByName("stateNode", mtTree);
        
        UniformizationRetypeOperator op = new UniformizationRetypeOperator() {
            
            @Override
            public void initAndValidate() { };

            @Override
            public double proposal() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        };
        op.initByName(
                "multiTypeTree", mtTree,
                "migrationModel", migModel);
        
        op.mtTree = mtTree;
        
        migModel.getQ(false).print();
        
        PrintStream outfile = new PrintStream("counts.txt");
        outfile.print("totalCounts");
        for (int c=0; c<mtTree.getNTypes(); c++)
            outfile.print(" counts" + c);
        outfile.println();
        
        for (int i=0; i<10000; i++) {
            MultiTypeNode srcNode = (MultiTypeNode)mtTree.getRoot().getLeft();
            op.retypeBranch(srcNode);
            outfile.print(srcNode.getChangeCount());
            
            int[] counts = new int[4];
            for (int j=0; j<srcNode.getChangeCount(); j++) {
                counts[srcNode.getChangeType(j)] += 1;
            }
            
            for (int c=0; c<mtTree.getNTypes(); c++)
                outfile.print(" " + counts[c]);
            
            outfile.println();
        }
        
        outfile.close();
    }

}

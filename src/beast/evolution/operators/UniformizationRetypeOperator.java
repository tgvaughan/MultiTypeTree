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
    
    public Input<Boolean> useSymmetrizedRatesInput = new Input<Boolean>(
            "useSymmetrizedRates",
            "Use symmetrized rate matrix to propose migration paths.", false);
    
    /**
     * Exception used to signal non-existence of allowed type sequence
     * between node types.
     */
    protected class NoValidPathException extends Exception {
        @Override
        public String getMessage() {
            return "No valid valid type sequence exists between chosen nodes.";
        }
    }
    
    
    /**
     * Sample the number of virtual events to occur along branch.
     * 
     * General strategy here is to:
     * 1. Draw u from Unif(0,1),
     * 2. Starting from zero, evaluate P(n leq 0|a,b) up until n=thresh
     * or P(n leq 0|a,b)>u.
     * 3. If P(n leq 0|a,b) has exceeded u, use that n. If not, use rejection
     * sampling to draw conditional on n being >= thresh.
     * 
     * @param typeStart Type at start (bottom) of branch
     * @param typeEnd Type at end (top) of branch
     * @param muL Expected unconditioned number of virtual events
     * @param Pba Probability of final type given start type
     * @param migrationModel Migration model to use.
     * @return number of virtual events.
     */
    private int drawEventCount(int typeStart, int typeEnd, double muL, double Pba,
            MigrationModel migrationModel, boolean sym) {
        
        int nVirt = 0;

        double u = Randomizer.nextDouble();
        double P_low_given_ab = 0.0;
        double acc = - muL - Math.log(Pba);
        double log_muL = Math.log(muL);
        
        do {
            //double offset = acc + nVirt*log_muL - Gamma.logGamma(nVirt+1);
            P_low_given_ab += Math.exp(Math.log(migrationModel.getRpowN(nVirt, sym).get(typeStart, typeEnd)) + acc);
            
            if (P_low_given_ab>u)
                return nVirt;

            nVirt += 1;
            acc += log_muL - Math.log(nVirt);
            
        } while (migrationModel.RpowSteadyN(sym)<0 || nVirt<migrationModel.RpowSteadyN(sym));
        
        int thresh = nVirt;
        
        // P_n_given_ab constant for n>= thresh: only need
        // to sample P(n|n>=thresh)
        do {
            nVirt = (int) Randomizer.nextPoisson(muL);
        } while (nVirt < thresh);

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
    protected double retypeBranch(Node srcNode) throws NoValidPathException {
        
        boolean sym = useSymmetrizedRatesInput.get();
        
        MigrationModel migrationModel = migrationModelInput.get();

        Node srcNodeP = srcNode.getParent();
        double t_srcNode = srcNode.getHeight();
        double t_srcNodeP = srcNodeP.getHeight();

        double L = t_srcNodeP-t_srcNode;

        int type_srcNode = ((MultiTypeNode)srcNode).getNodeType();
        int type_srcNodeP = ((MultiTypeNode)srcNodeP).getNodeType();

        // Pre-calculate some stuff:
        double muL = migrationModel.getMu(sym)*L;
        
        double Pba = MatrixFunctions.expm(
                migrationModel.getQ(sym)
                .mul(L)).get(type_srcNode,type_srcNodeP);

        // Abort if transition is impossible.
        if (Pba == 0.0)
            throw new NoValidPathException();
        
        // Catch for numerical errors
        if (Pba>1.0 || Pba<0.0) {
            System.err.println("Warning: matrix exponentiation resulted in rubbish.  Aborting move.");
            return Double.NEGATIVE_INFINITY;
        }
        
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
            
            // Check for FB algorithm error:
            if (fellThrough) {
                
                double sum1 = migrationModel.getRpowN(nVirt-i+1, sym).get(prevType, type_srcNodeP);
                double sum2 = 0;
                for (c = 0; c<mtTree.getNTypes(); c++) {
                    sum2 += migrationModel.getR(sym).get(prevType,c)
                            *migrationModel.getRpowN(nVirt-i, sym).get(c,type_srcNodeP);
                }
                
                System.err.println("Warning: FB algorithm failure.  Aborting move.");
                return Double.NEGATIVE_INFINITY;
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
        if (Pba>1.0 || Pba < 0.0) {
            System.err.println("Warning: matrix exponentiation resulted in rubbish.  Aborting move.");
            return Double.NEGATIVE_INFINITY;
        }
        
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
                "((1[deme='3']:50)[deme='1']:150,2[deme='1']:200)[deme='1']:0;";
        
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

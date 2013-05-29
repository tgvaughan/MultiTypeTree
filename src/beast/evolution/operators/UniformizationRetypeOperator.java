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
            + " rejection sampling is used. (Default 20.)", 50);
    
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
            MigrationModel migrationModel) {
        
        int nVirt;
        
        if (muL>rejectThresholdInput.get()) {
            // Rejection sample for large mu*L
            // (Avoids numerical difficulties produced by direct method.)
            
            double P_b_given_na;
            do {
                nVirt = (int) Randomizer.nextPoisson(muL);
            
                P_b_given_na = migrationModel.getRpowElement(nVirt, 1.0, typeEnd, typeStart);
            } while (Randomizer.nextDouble()>P_b_given_na);
            
        } else {
            // Direct sampling for smaller mu*L

            nVirt = 0;
            double u = Randomizer.nextDouble()*Pba;
            double poisAcc = Math.exp(-muL);
            u -= poisAcc*migrationModel.getRpowElement(0, 1.0, typeEnd, typeStart);
            while (u>0.0) {            
                nVirt += 1;
                
                poisAcc *= muL/nVirt;
                u -= poisAcc*migrationModel.getRpowElement(nVirt, 1.0, typeEnd, typeStart);
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
        
        MigrationModel migrationModel = migrationModelInput.get();

        Node srcNodeP = srcNode.getParent();
        double t_srcNode = srcNode.getHeight();
        double t_srcNodeP = srcNodeP.getHeight();

        double L = t_srcNodeP-t_srcNode;

        int type_srcNode = ((MultiTypeNode)srcNode).getNodeType();
        int type_srcNodeP = ((MultiTypeNode)srcNodeP).getNodeType();

        // Select number of virtual events:
        double Pba = migrationModel.getQexpElement(L, type_srcNodeP, type_srcNode);
        double muL = migrationModel.getMu()*L;        
        int nVirt = drawEventCount(type_srcNode, type_srcNodeP, muL, Pba, migrationModel);
        
        //System.out.println(nVirt);
        
        // Select times of virtual events:
        double[] times = new double[nVirt];
        for (int i = 0; i<nVirt; i++)
            times[i] = Randomizer.nextDouble()*L+t_srcNode;
        Arrays.sort(times);

        // Sample type changes from top to bottom of branch:
        int[] types = new int[nVirt];
        int lastType = type_srcNodeP;
        for (int i = nVirt; i>=1; i--) {
            double u2 = Randomizer.nextDouble()
                    *migrationModel.getRpowElement(i, 1.0, lastType, type_srcNode);
            int c;
            for (c = 0; c<mtTree.getNTypes(); c++) {
                u2 -= migrationModel.getRelement(lastType, c)
                        *migrationModel.getRpowElement(i-1, 1.0, c, type_srcNode);
                if (u2<0.0)
                    break;
            }

            types[i-1] = c;
            lastType = c;
        }

        double logProb = 0.0;

        // Add non-virtual type changes to branch, calculating probability
        // of path conditional on start type:
        ((MultiTypeNode)srcNode).clearChanges();
        lastType = type_srcNode;
        double lastTime = t_srcNode;
        for (int i = 0; i<nVirt; i++) {

            int nextType;
            if (i!=nVirt-1)
                nextType = types[i+1];
            else
                nextType = type_srcNodeP;

            if (nextType!=lastType) {

                // Add change to branch:
                ((MultiTypeNode)srcNode).addChange(nextType, times[i]);

                // Add probability contribution:
                logProb += migrationModel.getQelement(lastType, lastType)*(times[i]-lastTime)
                        +Math.log(migrationModel.getQelement(nextType, lastType));

                lastType = nextType;
                lastTime = times[i];
            }
        }
        logProb += migrationModel.getQelement(lastType, lastType)*(t_srcNodeP-lastTime);

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

            logProb += (thisTime-lastTime)*migrationModel.getQelement(lastCol, lastCol)
                    +Math.log(migrationModel.getQelement(thisCol, lastCol));

            lastTime = thisTime;
            lastCol = thisCol;
        }
        logProb += (t_srcNodeP-lastTime)*migrationModel.getQelement(lastCol, lastCol);

        // Adjust to account for end condition of path:
        logProb -= Math.log(
                migrationModel.getQexpElement(L, col_srcNodeP, col_srcNode));

        return logProb;
    }


    /**
     * Main method for testing.
     * 
     * @param args 
     */
    public static void main(String[] args) throws Exception {
       
        // Generate an ensemble of paths along a branch of a tree.
        
        // Assemble initial MultiTypeTree
        String newickStr =
                "((1[deme='1']:0.5)[deme='0']:0.5,2[deme='0']:1)[deme='0']:0;";
        
        MultiTypeTreeFromNewick mtTree = new MultiTypeTreeFromNewick();
        mtTree.initByName(
                "newick", newickStr,
                "typeLabel", "deme",
                "nTypes", 2);
        
        // Assemble migration model:
        RealParameter rateMatrix = new RealParameter("100 100");
        RealParameter popSizes = new RealParameter("7.0 7.0");
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
        
        PrintStream outfile = new PrintStream("counts.txt");
        outfile.println("count");
        for (int i=0; i<10; i++) {
            MultiTypeNode srcNode = (MultiTypeNode)mtTree.getRoot().getLeft();
            op.retypeBranch(srcNode);
            outfile.println(srcNode.getChangeCount());
        }
        outfile.close();
    }

}

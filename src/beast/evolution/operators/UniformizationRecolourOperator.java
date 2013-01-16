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
import beast.evolution.migrationmodel.MigrationModel;
import beast.evolution.tree.Node;
import beast.util.Randomizer;
import java.util.Arrays;

/**
 * Abstract class of operators on ColouredTrees which use the Fearnhead-Sherlock
 * uniformization/forward-backward algorithm for branch recolouring.
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public abstract class UniformizationRecolourOperator extends ColouredTreeOperator {
    
    public Input<MigrationModel> migrationModelInput = new Input<MigrationModel>(
            "migrationModel",
            "Migration model for proposal distribution", Input.Validate.REQUIRED);
    
    public Input<Integer> maxIterationsInput = new Input<Integer>(
            "maxIterations",
            "Places an upper bound on number of iterations before rejection. "
            + "(Default 10000.)",
            10000);
    
    /**
     * Recolour branch between srcNode and its parent.  Uses the combined
     * uniformization/forward-backward approach of Fearnhead and Sherlock (2006)
     * to condition on both the beginning and end states.
     *
     * @param srcNode
     * @return Probability of new state.
     */
    protected double recolourBranch(Node srcNode) throws RecolouringException {
        
        MigrationModel migrationModel = migrationModelInput.get();

        Node srcNodeP = srcNode.getParent();
        double t_srcNode = srcNode.getHeight();
        double t_srcNodeP = srcNodeP.getHeight();

        double L = t_srcNodeP-t_srcNode;

        int col_srcNode = cTree.getNodeColour(srcNode);
        int col_srcNodeP = cTree.getNodeColour(srcNodeP);

        // Select number of virtual events:
        double Pba = migrationModel.getQexpElement(L, col_srcNodeP, col_srcNode);
        double muL = migrationModel.getMu()*L;        
        
        double u1 = Randomizer.nextDouble()*Pba;
        int nVirt = 0;
        double poisAcc = Math.exp(-muL);
        u1 -= poisAcc*migrationModel.getRpowElement(0, 1.0, col_srcNodeP, col_srcNode);
        while (u1>0.0) {            
            nVirt += 1;
            
            if (maxIterationsInput.get() != null && nVirt>maxIterationsInput.get()) {
                System.err.println("WARNING: Maximum number of iterations "
                        + "in uniformized branch recolouring operator exceeded. "
                        + "Rejecting move.");
                return Double.NEGATIVE_INFINITY;
            }
            
            poisAcc *= muL/nVirt;
            u1 -= poisAcc*migrationModel.getRpowElement(nVirt, 1.0, col_srcNodeP, col_srcNode);
        }

        // Select times of virtual events:
        double[] times = new double[nVirt];
        for (int i = 0; i<nVirt; i++)
            times[i] = Randomizer.nextDouble()*L+t_srcNode;
        Arrays.sort(times);

        // Sample colour changes from top to bottom of branch:
        int[] colours = new int[nVirt];
        int lastCol = col_srcNodeP;
        for (int i = nVirt; i>=1; i--) {
            double u2 = Randomizer.nextDouble()
                    *migrationModel.getRpowElement(i, 1.0, lastCol, col_srcNode);
            int c;
            for (c = 0; c<cTree.getNColours(); c++) {
                u2 -= migrationModel.getRelement(lastCol, c)
                        *migrationModel.getRpowElement(i-1, 1.0, c, col_srcNode);
                if (u2<0.0)
                    break;
            }

            colours[i-1] = c;
            lastCol = c;
        }

        double logProb = 0.0;

        // Add non-virtual colour changes to branch, calculating probability
        // of path conditional on start colour:
        setChangeCount(srcNode, 0);
        lastCol = col_srcNode;
        double lastTime = t_srcNode;
        for (int i = 0; i<nVirt; i++) {

            int nextCol;
            if (i!=nVirt-1)
                nextCol = colours[i+1];
            else
                nextCol = col_srcNodeP;

            if (nextCol!=lastCol) {

                // Add change to branch:
                addChange(srcNode, nextCol, times[i]);

                // Add probability contribution:
                logProb += migrationModel.getQelement(lastCol, lastCol)*(times[i]-lastTime)
                        +Math.log(migrationModel.getQelement(nextCol, lastCol));

                lastCol = nextCol;
                lastTime = times[i];
            }
        }
        logProb += migrationModel.getQelement(lastCol, lastCol)*(t_srcNodeP-lastTime);

        // Adjust probability to account for end condition:
        logProb -= Math.log(Pba);

        // Return probability of path given boundary conditions:
        return logProb;
    }
    
    
    /**
     * Recolour branches with nChanges between srcNode and the root (srcNode's
     * parent) and nChangesSister between the root and srcNode's sister.
     *
     * @param srcNode
     * @return Probability of new state.
     */
    protected double recolourRootBranches(Node srcNode) throws RecolouringException {
        
        MigrationModel migrationModel = migrationModelInput.get();

        double logProb = 0.0;

        Node srcNodeP = srcNode.getParent();
        Node srcNodeS = getOtherChild(srcNodeP, srcNode);

        // Select new root colour:
        double u = Randomizer.nextDouble()*migrationModel.getTotalPopSize();
        int rootCol;
        for (rootCol = 0; rootCol<cTree.getNColours(); rootCol++) {
            u -= migrationModel.getPopSize(rootCol);
            if (u<0)
                break;
        }
        setNodeColour(srcNodeP, rootCol);

        // Incorporate probability of choosing new root colour:
        logProb += Math.log(migrationModel.getPopSize(rootCol)
                /migrationModel.getTotalPopSize());

        // Recolour branches conditional on root colour:
        logProb += recolourBranch(srcNode);
        logProb += recolourBranch(srcNodeS);

        // Return probability of new colouring given boundary conditions:
        return logProb;
    }

    /**
     * Obtain probability of the current migratory path above srcNode.
     *
     * @param srcNode
     * @return Path probability.
     */
    protected double getBranchColourProb(Node srcNode) {
        
        MigrationModel migrationModel = migrationModelInput.get();

        double logProb = 0.0;

        Node srcNodeP = srcNode.getParent();
        double t_srcNode = srcNode.getHeight();
        double t_srcNodeP = srcNodeP.getHeight();
        double L = t_srcNodeP-t_srcNode;
        int col_srcNode = cTree.getNodeColour(srcNode);
        int col_srcNodeP = cTree.getNodeColour(srcNodeP);

        // Probability of branch conditional on start colour:
        double lastTime = t_srcNode;
        int lastCol = col_srcNode;
        for (int i = 0; i<cTree.getChangeCount(srcNode); i++) {
            double thisTime = cTree.getChangeTime(srcNode, i);
            int thisCol = cTree.getChangeColour(srcNode, i);

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
     * Obtain joint probability of colouring along branches between srcNode and
     * the root, the sister of srcNode and the root, and the node colour of the
     * root.
     *
     * @param srcNode
     * @return
     */
    protected double getRootBranchColourProb(Node srcNode) {
        
        MigrationModel migrationModel = migrationModelInput.get();

        double logProb = 0.0;

        Node srcNodeP = srcNode.getParent();
        Node srcNodeS = getOtherChild(srcNodeP, srcNode);
        int col_srcNodeP = cTree.getNodeColour(srcNodeP);

        // Probability of choosing root colour:
        logProb += Math.log(migrationModel.getPopSize(col_srcNodeP)
                /migrationModel.getTotalPopSize());

        // Probability of branch colours conditional on node colours:
        logProb += getBranchColourProb(srcNode);
        logProb += getBranchColourProb(srcNodeS);

        return logProb;
    }
    
}

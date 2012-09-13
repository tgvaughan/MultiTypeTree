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
package beast.evolution.operator;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.evolution.migrationmodel.MigrationModel;
import beast.evolution.tree.Node;
import beast.util.PoissonRandomizer;
import beast.util.Randomizer;
import java.util.Arrays;

/**
 * Wilson-Balding branch swapping operator applied to coloured trees.
 *
 * @author Tim Vaughan
 */
@Description("Implements the unweighted Wilson-Balding branch"
+"swapping move.  This move is similar to one proposed by WILSON"
+"and BALDING 1998 and involves removing a subtree and"
+"re-attaching it on a new parent branch. "
+"See <a href='http://www.genetics.org/cgi/content/full/161/3/1307/F1'>picture</a>."
+"This version recolours each newly generated branch by drawing a"
+"path from the migration model conditional on the colours at the"
+"branch ends.")
public class ColouredWilsonBalding extends ColouredTreeOperator {

    public Input<MigrationModel> migrationModelInput = new Input<MigrationModel>(
            "migrationModel",
            "Migration model for proposal distribution", Validate.REQUIRED);
    public Input<Double> alphaInput = new Input<Double>("alpha",
            "Root height proposal parameter", Validate.REQUIRED);
    private MigrationModel migrationModel;
    private double alpha;

    @Override
    public void initAndValidate() {
    }

    @Override
    public double proposal() {
        cTree = colouredTreeInput.get();
        tree = m_tree.get(this);
        migrationModel = migrationModelInput.get();
        alpha = alphaInput.get();

        // Check that operator can be applied to tree:
        if (tree.getLeafNodeCount()<3)
            throw new IllegalStateException("Tree too small for"
                    +" ColouredWilsonBaldingRandom operator.");

        // Select source node:
        Node srcNode;
        do {
            srcNode = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
        } while (invalidSrcNode(srcNode));
        Node srcNodeP = srcNode.getParent();
        Node srcNodeS = getOtherChild(srcNodeP, srcNode);
        double t_srcNode = srcNode.getHeight();
        double t_srcNodeP = srcNodeP.getHeight();
        double t_srcNodeS = srcNodeS.getHeight();

        // Select destination branch node:
        Node destNode;
        do {
            destNode = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
        } while (invalidDestNode(srcNode, destNode));
        Node destNodeP = destNode.getParent();
        double t_destNode = destNode.getHeight();

        // Handle special cases involving root:

        if (destNode.isRoot()) {
            // FORWARD ROOT MOVE

            double logHR = 0.0;

            // Record probability of current colouring:
            logHR += getBranchColourProb(srcNode);

            // Record old srcNode parent height and change count:
            double oldTime = t_srcNodeP;
            int oldChangeCount = cTree.getChangeCount(srcNode);

            // Record srcNode grandmother height:
            double t_srcNodeG = srcNodeP.getParent().getHeight();

            // Choose new root height:
            double newTime = t_destNode+Randomizer.nextExponential(1.0/(alpha*t_destNode));

            // Implement tree changes:
            disconnectBranch(srcNode);
            connectBranchToRoot(srcNode, destNode, newTime);
            setRoot(srcNodeP);

            // Recolour root branches:
            logHR -= recolourRootBranches(srcNode);

            int newChangeCount = cTree.getChangeCount(srcNode)
                    +cTree.getChangeCount(destNode);

            // Return HR:
            logHR += Math.log(alpha*t_destNode)
                    +(1.0/alpha)*(newTime/t_destNode-1.0)
                    -Math.log(t_srcNodeG-Math.max(t_srcNode, t_srcNodeS));

            return logHR;
        }

        if (srcNodeP.isRoot()) {
            // BACKWARD ROOT MOVE

            double logHR = 0.0;

            // Incorporate probability of current colouring:
            logHR += getRootBranchColourProb(srcNode);

            // Record old srcNode parent height and combined change count
            // for srcNode and her sister:
            double oldTime = t_srcNodeP;
            int oldChangeCount = cTree.getChangeCount(srcNode)
                    +cTree.getChangeCount(srcNodeS);

            // Choose height of new attachement point:
            double min_newTime = Math.max(t_srcNode, t_destNode);
            double t_destNodeP = destNodeP.getHeight();
            double span = t_destNodeP-min_newTime;
            double newTime = min_newTime+span*Randomizer.nextDouble();

            // Implement tree changes:
            disconnectBranchFromRoot(srcNode);
            connectBranch(srcNode, destNode, newTime);
            srcNodeS.setParent(null);
            setRoot(srcNodeS);

            // Recolour new branch:
            logHR -= recolourBranch(srcNode);

            int newChangeCount = cTree.getChangeCount(srcNode);

            // Return HR:
            logHR += Math.log(t_destNodeP-Math.max(t_srcNode, t_destNode))
                    -Math.log(alpha*t_srcNodeS)
                    -(1.0/alpha)*(oldTime/t_srcNodeS-1.0);

            return logHR;
        }

        // NON-ROOT MOVE

        double logHR = 0.0;

        // Incorporate probability of current colouring.
        logHR += getBranchColourProb(srcNode);

        // Record old srcNodeP height and change count:
        double oldTime = t_srcNodeP;
        int oldChangeCount = cTree.getChangeCount(srcNode);

        // Record srcNode grandmother height:
        double t_srcNodeG = srcNodeP.getParent().getHeight();

        // Choose height of new attachment point:
        double min_newTime = Math.max(t_destNode, t_srcNode);
        double t_destNodeP = destNodeP.getHeight();
        double span = t_destNodeP-min_newTime;
        double newTime = min_newTime+span*Randomizer.nextDouble();

        // Implement tree changes:
        disconnectBranch(srcNode);
        connectBranch(srcNode, destNode, newTime);

        // Recolour new branch:
        logHR -= recolourBranch(srcNode);

        int newChangeCount = cTree.getChangeCount(srcNode);

        // Return HR:
        logHR += Math.log(t_destNodeP-Math.max(t_srcNode, t_destNode))
                -Math.log(t_srcNodeG-Math.max(t_srcNode, t_srcNodeS));

        return logHR;
    }

    /**
     * Returns true if srcNode CANNOT be used for the CWBR move.
     *
     * @param srcNode
     * @return True if srcNode invalid.
     */
    private boolean invalidSrcNode(Node srcNode) {

        if (srcNode.isRoot())
            return true;

        Node parent = srcNode.getParent();

        // This check is important in avoiding situations where it is
        // impossible to choose a valid destNode:
        if (parent.isRoot()) {

            Node sister = getOtherChild(parent, srcNode);

            if (sister.isLeaf())
                return true;

            if (srcNode.getHeight()>=sister.getHeight())
                return true;
        }

        return false;
    }

    /**
     * Returns true if destNode CANNOT be used for the CWBR move in conjunction
     * with srcNode.
     *
     * @param srcNode
     * @param destNode
     * @return True if destNode invalid.
     */
    private boolean invalidDestNode(Node srcNode, Node destNode) {

        if (destNode==srcNode
                ||destNode==srcNode.getParent()
                ||destNode.getParent()==srcNode.getParent())
            return true;

        Node srcNodeP = srcNode.getParent();
        Node destNodeP = destNode.getParent();

        if (destNodeP!=null&&(destNodeP.getHeight()<=srcNode.getHeight()))
            return true;

        return false;
    }

    /**
     * Recolour branch between srcNode and its parent with rate fixed by the
     * tuning parameter mu.
     *
     * @param srcNode
     * @return Probability of new state.
     */
    private double recolourBranch(Node srcNode) {

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
        while (u1>0) {
            nVirt += 1;
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
    private double recolourRootBranches(Node srcNode) {

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
    private double getBranchColourProb(Node srcNode) {

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
    private double getRootBranchColourProb(Node srcNode) {

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
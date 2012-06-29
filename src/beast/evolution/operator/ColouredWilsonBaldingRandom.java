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
import beast.core.parameter.RealParameter;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.math.GammaFunction;
import beast.util.PoissonRandomizer;
import beast.util.Randomizer;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.util.Arrays;

/**
 * Wilson-Balding branch swapping operator applied to coloured trees.
 * This version simply assigns randomly chosen colour changes to new
 * branches.
 *
 * @author Tim Vaughan
 */
@Description("Implements the unweighted Wilson-Balding branch"
        + "swapping move.  This move is similar to one proposed by WILSON"
        + "and BALDING 1998 and involves removing a subtree and"
        + "re-attaching it on a new parent branch. " +
        "See <a href='http://www.genetics.org/cgi/content/full/161/3/1307/F1'>picture</a>."
        + "This version generates random colouring along branches altered by"
        + "the operator.")
public class ColouredWilsonBaldingRandom extends ColouredTreeOperator {

    public Input<RealParameter> muInput = new Input<RealParameter>("mu",
            "Migration rate for proposal distribution", Validate.REQUIRED);

    @Override
    public void initAndValidate() {}

    @Override
    public double proposal() {
        cTree = colouredTreeInput.get();
        Tree tree = m_tree.get(this);

        Node i = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
        double t_i = i.getHeight();

        if (i.isRoot())
            return Double.NEGATIVE_INFINITY;

        Node iP = i.getParent();
        double t_iP = iP.getHeight();

        Node j;
        Node jP;

        do {
            j = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
            jP = j.getParent();
        } while (j==i);
//        } while ((jP != null && jP.getHeight() <= i.getHeight()) || (i.getNr() == j.getNr()));

        double t_j = j.getHeight();

        if (iP == jP || iP == j || iP == i || (jP != null && jP.getHeight()<=i.getHeight()))
            return Double.NEGATIVE_INFINITY;


        int nCount = tree.getRoot().getNodeCount();

        if (j.isRoot()) {

            // Record probability of old configuration:
            Node CiP = getOtherChild(iP, i);
            double t_CiP = CiP.getHeight();

            Node PiP = iP.getParent();
            double t_PiP = PiP.getHeight();

            double probOldConfig = getPathProb(i)/(t_PiP-Math.max(t_i,t_CiP));

            double span=2.0*(t_j-
                    Math.max(j.getLeft().getHeight(), j.getRight().getHeight()));
            double newTime = t_j + span*Randomizer.nextDouble();


            // Implement tree changes:
            disconnectBranch(i);
            connectBranchToRoot(i, j, newTime);

            tree.setRoot(iP);

            if (m_tree.get().getRoot().getNodeCount() != nCount)
                throw new RuntimeException("Error: Lost a child during j-root move!!!");

            // Recolour branches:
            recolourBranch(i);
            recolourBranch(j);

            // Reject if colours inconsistent:
            if ((cTree.getFinalBranchColour(i) != cTree.getNodeColour(iP))
                    || cTree.getFinalBranchColour(j) != cTree.getNodeColour(iP))
                return Double.NEGATIVE_INFINITY;

            // Record probability of new configuration:

            double probNewConfig = getPathProb(i)*getPathProb(j)/span;

            // Calculate Hastings ratio:
            double HR = probOldConfig/probNewConfig;

            Tree helper = cTree.getFlattenedTree();

            // reject if colour change doesn't change anything
            if (cTree.hasSingleChildrenWithoutColourChange(helper.getRoot()))     // invalid tree
                return Double.NEGATIVE_INFINITY;

            return Math.log(HR);

        }


        else if (iP.isRoot()) {

            // Moving subtree connected to root node.
            double t_jP = jP.getHeight();

            Node CiP = getOtherChild(iP, i);
            double t_CiP = CiP.getHeight();

            // Record probability of old configuration:
            double span = 2.0*(CiP.getHeight()
                    -Math.max((CiP.getLeft()!=null)?CiP.getLeft().getHeight():0,
                    (CiP.getRight()!=null)?CiP.getRight().getHeight():0));
            double probOldConfig = getPathProb(i)*getPathProb(CiP)/span;

            // Select height of new branch connection:
            double newTimeMin = Math.max(i.getHeight(), j.getHeight());
            double newTimeMax = jP.getHeight();
            double newTime = newTimeMin +
                    Randomizer.nextDouble()*(newTimeMax-newTimeMin);

            Node sister = getOtherChild(i.getParent(), i);

            // Implement tree changes:
            disconnectBranchFromRoot(i);
            connectBranch(i, j, newTime);

            sister.setParent(null);
            tree.setRoot(sister);

            if (m_tree.get().getRoot().getNodeCount() != nCount)
                throw new RuntimeException("Error: Lost a child during iP-root move!!!");


            // Recolour new branch:
            recolourBranch(i);


            // Reject if colours inconsistent:
            if (cTree.getFinalBranchColour(i) != cTree.getNodeColour(iP))
                return Double.NEGATIVE_INFINITY;

            // Calculate probability of new configuration
            double probNewConfig = getPathProb(i)/
                    (t_jP - Math.max(t_j,t_i));

            // Calculate Hastings ratio:
            double HR = probOldConfig/probNewConfig;

            Tree helper = cTree.getFlattenedTree();

            // reject if colour change doesn't change anything
            if (cTree.hasSingleChildrenWithoutColourChange(helper.getRoot()))     // invalid tree
                return Double.NEGATIVE_INFINITY;

            return Math.log(HR);
        }

        else {


            // Case where root is not involved:

            double t_jP = jP.getHeight();

            Node CiP = getOtherChild(iP, i);
            double t_CiP = CiP.getHeight();

            Node PiP = iP.getParent();
            double t_PiP = PiP.getHeight();

            // Record probability of old branch colouring (needed for HR):
            double oldColourProb = getPathProb(i);

            // Select height of new node:
            double newTimeMin = Math.max(i.getHeight(), j.getHeight());
            double newTimeMax = jP.getHeight();
            double newTime = newTimeMin +
                    Randomizer.nextDouble()*(newTimeMax-newTimeMin);

            // Implement tree changes:
            disconnectBranch(i);
            connectBranch(i, j, newTime);

            if (m_tree.get().getRoot().getNodeCount() != nCount)
                throw new RuntimeException("Error: Lost a child during non-root move!!!");

            // Recolour new branch:
            recolourBranch(i);

            // Reject if colours inconsistent:
            if (cTree.getFinalBranchColour(i) != cTree.getNodeColour(iP))
                return Double.NEGATIVE_INFINITY;

            // Calculate probability of new branch colouring:
            double newColourProb = getPathProb(i);

            double HR = (oldColourProb/(t_PiP-Math.max(t_i,t_CiP)))/
                    (newColourProb/(t_jP-Math.max(t_i,t_j)));

            try{

                cTree.setInputValue("tree", tree);

            }catch(Exception e){System.out.println(e.getMessage());}


            Tree helper = cTree.getFlattenedTree();

            // reject if colour change doesn't change anything
            if (cTree.hasSingleChildrenWithoutColourChange(helper.getRoot()))     // invalid tree
                return Double.NEGATIVE_INFINITY;

            return Math.log(HR);
        }
    }

    /**
     * Randomly recolour the branch starting at node.
     *
     * @param node
     */
    private void recolourBranch(Node node)  {

		double t = node.getParent().getHeight() - node.getHeight();
		int nChanges = PoissonRandomizer.nextInt(muInput.get().getValue()*t);

        if (nChanges > 0){
            double initialTime = node.getHeight();
            double finalTime = node.getParent().getHeight();

            double[] times = getTimes(initialTime, finalTime, nChanges);
            int[] colours = getColours(times.length);

            // Record new colour change events:
            cTree.setChangeCount(node, times.length);
            cTree.setChangeColours(node, colours);
            cTree.setChangeTimes(node, times);
        }
        else
            cTree.setChangeCount(node, 0);

    }

    /**
     * Randomly select colour change times uniformly along branch.
     *
     * @param initialTime
     * @param finalTime
     * @return Array of colour change times.
     */
    private double[] getTimes(double initialTime, double finalTime, int nChanges) {


        double T = finalTime-initialTime;

        // Assign random times between initialTime and finalTime:
        double[] times = new double[nChanges];
        for (int i=0; i<nChanges; i++)
            times[i] = T*Randomizer.nextDouble() + initialTime;
        Arrays.sort(times);

        return times;
    }

    /**
     * Randomly assign nChanges colour changes to branch.
     *
     * @param nChanges
     * @return Array of colours.
     */
    private int[] getColours(int nChanges) {

        int[] colours = new int[nChanges];
        int nColours = cTree.getNColours();

        for (int i=0; i<nChanges; i++) {
            colours[i] = Randomizer.nextInt(nColours);
        }

        return colours;
    }

    /**
     * Return probability of a particular migratory path
     * along branch starting at node being chosen by
     * random colouring algorithm.
     *
     * @param node
     * @return Probability of path.
     */
    private double getPathProb(Node node) {

        // P(path) = P(colours|n)*P(n)

		double t = node.getParent().getHeight()-node.getHeight();

        int nChanges = cTree.getChangeCount(node);
        int nColours = cTree.getNColours();
        double mu = muInput.get().getValue();

        double Pn = Math.exp(-mu*t + Math.log(mu*t)*nChanges
                - GammaFunction.lnGamma(nChanges+1));

        double Pcol = Math.pow(1.0/(nColours-1), nChanges);

        return Pcol*Pn;
    }

    /**
     * Main method for debugging.
     *
     * @param args
     */
    public static void main (String[] args) {

        ColouredWilsonBaldingRandom wb = new ColouredWilsonBaldingRandom();

		for (int i=0; i<10000; i++)
	        System.out.println(PoissonRandomizer.nextInt(10));
    }

}
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
import com.google.common.collect.Lists;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.math.distribution.PoissonDistribution;
import org.apache.commons.math.distribution.PoissonDistributionImpl;

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
	
	public Input<RealParameter> rootScaleInput = new Input<RealParameter>(
			"rootSccale", "Scaling parameter for root height changes",
			Validate.REQUIRED);
	
	private double mu, rootScale;

    @Override
    public void initAndValidate() {}

    @Override
    public double proposal() {
        cTree = colouredTreeInput.get();
        Tree tree = m_tree.get(this);
		mu = muInput.get().getValue();
		rootScale = rootScaleInput.get().getValue();

		// Select source node:
        Node srcNode = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
        double t_srcNode = srcNode.getHeight();

		// Reject outright if srcNode is root:
        if (srcNode.isRoot())
            return Double.NEGATIVE_INFINITY;

        Node srcNodeP = srcNode.getParent();
        double t_srcNodeP = srcNodeP.getHeight();

		// Select destination branch node:
        Node destNode;
        do {
            destNode = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
        } while (destNode==srcNode);
		
        Node destNodeP = destNode.getParent();
        double t_destNode = destNode.getHeight();

		// Reject outright in certain cases:
        if (srcNodeP == destNodeP
				|| srcNodeP == destNode
				|| srcNodeP == srcNode
				|| (destNodeP != null && destNodeP.getHeight()<=srcNode.getHeight()))
            return Double.NEGATIVE_INFINITY;

        int nCount = tree.getRoot().getNodeCount();
		
		// Initialise variable to keep track of HR:
		double logHR = 0;

        if (destNode.isRoot()) {
			// Moving subtree to new tree root
			
			// Choose new root height:
            double span=2.0*(t_destNode-
                    Math.max(destNode.getLeft().getHeight(), destNode.getRight().getHeight()));
            double newTime = t_destNode + span*Randomizer.nextDouble();
			
			// Choose number of new colour changes to generate:
			double L = (newTime - t_srcNode) + (newTime - t_destNode);
			int newChangeCount = PoissonRandomizer.nextInt(mu*L);
			
			// Include probability of chosen move in HR:
			logHR -= getLogMoveProb(srcNode, destNode, newTime, newChangeCount);
			
			// Include probability of reverse move in HR:
			Node reverseDestNode = getOtherChild(srcNodeP, srcNode);
			logHR += getLogMoveProb(srcNode, reverseDestNode, t_srcNodeP,
					cTree.getChangeCount(srcNodeP));

            // Implement tree changes:
            disconnectBranch(srcNode);
            connectBranchToRoot(srcNode, destNode, newTime);
            tree.setRoot(srcNodeP);
			
			// Recolour root branches, forcing reject if inconsistent:
			if (!recolourRootBranches(srcNode, newChangeCount))
				return Double.NEGATIVE_INFINITY;

            if (m_tree.get().getRoot().getNodeCount() != nCount)
                throw new RuntimeException("Error: Lost a child during j-root move!!!");

			/*
            Tree helper = cTree.getFlattenedTree();
            // reject if colour change doesn't change anything
            if (cTree.hasSingleChildrenWithoutColourChange(helper.getRoot()))     // invalid tree
                return Double.NEGATIVE_INFINITY;
			*/

            return logHR;

        } else if (srcNodeP.isRoot()) {
            // Moving subtree connected to root node.
			
			// Choose height of new attachement point:
            double t_destNodeP = destNodeP.getHeight();
			double span = t_destNodeP - Math.max(t_srcNode,t_destNode);
			double newTime = t_destNode + span*Randomizer.nextDouble();
			
			// Choose number of new colour changes to generate:
			double L = newTime - t_srcNode;
			int newChangeCount = PoissonRandomizer.nextInt(mu*L);
			
			// Include probability of chosen move in HR:
			logHR -= getLogMoveProb(srcNode, destNode, newTime, newChangeCount);
			
			// Include probability of reverse move in HR:
			Node reverseDestNode = getOtherChild(srcNodeP, srcNode);
			logHR += getLogMoveProb(srcNode, reverseDestNode, t_srcNodeP,
					cTree.getChangeCount(srcNode));

            // Implement tree changes:
            disconnectBranchFromRoot(srcNode);
            connectBranch(srcNode, destNode, newTime);			
			Node srcNodeSister = getOtherChild(srcNode.getParent(), srcNode);
            srcNodeSister.setParent(null);
            tree.setRoot(srcNodeSister);

            if (m_tree.get().getRoot().getNodeCount() != nCount)
                throw new RuntimeException("Error: Lost a child during iP-root move!!!");

            // Recolour new branch, rejecting outright if inconsistent:
			if (!recolourBranch(srcNode, nCount))
				return Double.NEGATIVE_INFINITY;


            // Raise exception if colour change doesn't change anything
            Tree helper = cTree.getFlattenedTree();
            if (cTree.hasSingleChildrenWithoutColourChange(helper.getRoot()))
                throw new RuntimeException("Error: "
						+ "CWBR operator proposing invalid moves. "
						+ "This is a BUG!");

            return logHR;
        }

        else {
            // Root is not involved:

            double t_jP = destNodeP.getHeight();

            Node CiP = getOtherChild(srcNodeP, srcNode);
            double t_CiP = CiP.getHeight();

            Node PiP = srcNodeP.getParent();
            double t_PiP = PiP.getHeight();

            // Record probability of old branch colouring (needed for HR):
            double oldColourProb = getPathProb(srcNode);

            // Select height of new node:
            double newTimeMin = Math.max(srcNode.getHeight(), destNode.getHeight());
            double newTimeMax = destNodeP.getHeight();
            double newTime = newTimeMin +
                    Randomizer.nextDouble()*(newTimeMax-newTimeMin);

            // Implement tree changes:
            disconnectBranch(srcNode);
            connectBranch(srcNode, destNode, newTime);

            if (m_tree.get().getRoot().getNodeCount() != nCount)
                throw new RuntimeException("Error: Lost a child during non-root move!!!");

            // Recolour new branch:
            recolourBranch(srcNode);

            // Reject if colours inconsistent:
            if (cTree.getFinalBranchColour(srcNode) != cTree.getNodeColour(srcNodeP))
                return Double.NEGATIVE_INFINITY;

            // Calculate probability of new branch colouring:
            double newColourProb = getPathProb(srcNode);

            double HR = (oldColourProb/(t_PiP-Math.max(t_srcNode,t_CiP)))/
                    (newColourProb/(t_jP-Math.max(t_srcNode,t_destNode)));

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
	 * Recolour branch between srcNode and its parent with nChanges.
	 * 
	 * @param srcNode
	 * @param nChanges
	 * @return True if new colouring consistent with parent's node colour.
	 */
	private boolean recolourBranch(Node srcNode, int nChanges) {
		
		Node srcNodeParent = srcNode.getParent();
		double t_srcNode = srcNode.getHeight();
		double t_srcNodeParent = srcNodeParent.getHeight();
		
		double L = t_srcNodeParent - t_srcNode;
		
		// Obtain new colours and times:
		double[] times = getTimes(L, nChanges);
		int[] colours= getColours(cTree.getNodeColour(srcNode), nChanges);
		
		// Determine final colour on branch:
		int endCol;
		if (nChanges>0)
			endCol = colours[colours.length-1];
		else
			endCol = cTree.getNodeColour(srcNode);
		
		// Reject outright if final colour doesn't match parent's node colour:
		if (cTree.getNodeColour(srcNodeParent) != endCol)
			return false;

		// Clear existing changes in preparation for adding replacements:
		setChangeCount(srcNode, 0);
		
		// Set colours along branch between srcNode and its parent:
		for (int i=0; i<nChanges; i++) {
			addChange(srcNode, colours[i], times[i]+t_srcNode);
		}
		
		return true;
	}

	/**
	 * Recolour branches with nChanges between srcNode, the root
	 * (srcNode's parent) and srcNode's sister.
	 * 
	 * @param srcNode
	 * @param nChanges
	 * @return True if recolour successful.
	 */
	private boolean recolourRootBranches(Node srcNode, int nChanges) {
		
		Node root = srcNode.getParent();
		Node srcNodeSister = getOtherChild(root, srcNode);
		
		double t_srcNode = srcNode.getHeight();
		double t_srcNodeSister = srcNodeSister.getHeight();
		double t_root = root.getHeight();
		
		double Lfirst = t_root - t_srcNode;
		double Lsecond = t_root - t_srcNodeSister;
		double L = Lfirst + Lsecond;
		
		// Obtain new colours and times:
		double[] times = getTimes(L, nChanges);
		int[] colours = getColours(cTree.getNodeColour(srcNode), nChanges);
		
		// Determine final colour on two-branch path:
		int endCol;
		if (nChanges>0)
			endCol = colours[nChanges-1];
		else
			endCol = cTree.getNodeColour(srcNode);
		
		// Direct operator to reject outright if final colour doesn't match
		// colour on sister node:
		if (endCol != cTree.getNodeColour(srcNodeSister))
			return false;
		
		// Clear colour changes in preparation for adding replacements:
		setChangeCount(srcNode, 0);
		setChangeCount(srcNodeSister, 0);

		// Set colours along branch between srcNode and root:
		int endColFirst = cTree.getNodeColour(srcNode);
		int i=0;
		while (times[i]<Lfirst) {
			addChange(srcNode, colours[i], times[i]+t_srcNode);
			endColFirst = colours[i];
			i += 1;
		}
		
		// Set root node colour on the way past:
		cTree.setNodeColour(root, endColFirst);
		
		// Set colours along branch between srcNodeSister and root:
		i=colours.length-1;
		while (i>=0 && times[i]>Lfirst) {
			double timeOnSister = Lsecond - (times[i]-Lfirst) + t_srcNodeSister;
			int changeColour;
			if (i>0)
				changeColour = colours[i-1];
			else
				changeColour = cTree.getNodeColour(root);
			
			addChange(srcNodeSister, changeColour, timeOnSister);
			i -= 1;
		}
		
		// Report a successful recolour:
		return true;
	}

    /**
     * Randomly select colour change times uniformly along branch.
     *
     * @param initialTime
     * @param finalTime
     * @return Array of colour change times.
     */
    private double[] getTimes(double L, int nChanges) {

        // Assign random times between initialTime and finalTime:
        double[] times = new double[nChanges];
        for (int i=0; i<nChanges; i++)
            times[i] = L*Randomizer.nextDouble();
        Arrays.sort(times);

        return times;
    }

    /**
     * Randomly assign nChanges colour changes to branch.
     *
     * @param nChanges
     * @return Array of colours.
     */
    private int[] getColours(int startColour, int nChanges) {

        int[] colours = new int[nChanges];
        int nColours = cTree.getNColours();

		int lastCol = startColour;
        for (int i=0; i<nChanges; i++) {
			int newCol;
			do {
	            newCol = Randomizer.nextInt(nColours);
			} while (newCol == lastCol);
            colours[i] = newCol;
			lastCol = newCol;
        }

        return colours;
    }
	
	/**
	 * Obtain the probability of selecting a move which takes the current
	 * state of the tree to the new one specified by the arguments.
	 * 
	 * Note that state changes which are identical besides the positioning
	 * of the colour changes are grouped under the same move.
	 * 
	 * @param srcNode
	 * @param destNode
	 * @param t_srcNodePNew
	 * @return 
	 */
	private double getLogMoveProb(Node srcNode, Node destNode,
			double t_srcNodePNew, int nColourChangesNew) {
		
		double logP = 0;
		
		double t_srcNode = srcNode.getHeight();
		double t_destNode = destNode.getHeight();
		
		double L, h;

		if (destNode.isRoot()) {
			
			// Range over which root height was chosen:
			double tMax_destNodeC = Math.max(
					srcNode.getLeft().getHeight(),
					srcNode.getRight().getHeight());
			h = 2.0*(t_destNode - tMax_destNodeC);

			// Length of new branch to recolour:
			L = 2*t_srcNodePNew - t_srcNode - t_destNode;
						
		} else {
			
			// Range over which new node height was chosen:
			double t_destNodeP = destNode.getParent().getHeight();
			h = t_destNodeP - Math.max(t_srcNode, t_destNode);

			// Length of new branch to recolour:
			L = t_srcNodePNew - t_srcNode;
		}

		// Probability of chosen root height:
		logP += Math.log(1.0/h);

		// Probability of number of colour changes:
		logP += -mu*L + nColourChangesNew*Math.log(mu*L)
			- GammaFunction.lnGamma(nColourChangesNew+1);
		
		// Probability of particular colours chosen:
		logP += nColourChangesNew*Math.log(1.0/(cTree.getNColours()-1));
		
		// Probability of a particular choice of the random numbers used
		// to select the colour change times:
		logP += -GammaFunction.lnGamma(nColourChangesNew+1);
		
		return logP;
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
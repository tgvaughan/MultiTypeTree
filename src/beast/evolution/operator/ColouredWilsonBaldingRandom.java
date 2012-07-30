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
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.math.GammaFunction;
import beast.util.PoissonRandomizer;
import beast.util.Randomizer;
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

    public Input<Double> muInput = new Input<Double>("mu",
            "Migration rate for proposal distribution", Validate.REQUIRED);
	
	public Input<Double> alphaInput = new Input<Double>("alpha",
			"Root height proposal parameter", Validate.REQUIRED); 
	
	private double mu, alpha;

    @Override
    public void initAndValidate() {}

    @Override
    public double proposal() {
        cTree = colouredTreeInput.get();
        tree = m_tree.get(this);
		mu = muInput.get();
		alpha = alphaInput.get();

		// Select source node:
		Node srcNode;
		do {
			srcNode = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
		} while (srcNode.isRoot());
        double t_srcNode = srcNode.getHeight();

		// Record source node details:
        Node srcNodeP = srcNode.getParent();
        double t_srcNodeP = srcNodeP.getHeight();

		// Select destination branch node:
        Node destNode, destNodeP;
        do {
            destNode = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
			destNodeP = destNode.getParent();
        } while (destNode==srcNode
				|| srcNodeP == destNode
				|| srcNodeP == destNodeP
				|| (destNodeP != null && destNodeP.getHeight()<=t_srcNode));
		
		// Record destination node details:
        double t_destNode = destNode.getHeight();
		
		// Initialise variable to keep track of HR:
		double logHR = 0;

		// Handle special cases involving root:
		
        if (destNode.isRoot()) {
			// FORWARD ROOT MOVE
			
			// Record old srcNode parent height and change count:
			double oldTime = t_srcNodeP;
			int oldChangeCount = cTree.getChangeCount(srcNode);
			
			// Record srcNode sister and grandmother heights:
			double t_srcNodeG = srcNodeP.getParent().getHeight();
			double t_srcNodeS = getOtherChild(srcNodeP, srcNode).getHeight();
			
			// Choose new root height:
            double newTime = t_destNode + Randomizer.nextExponential(alpha/t_destNode);
			
			// Choose number of new colour changes to generate:
			double L = (newTime - t_srcNode) + (newTime - t_destNode);
			int newChangeCount = PoissonRandomizer.nextInt(mu*L);

            // Implement tree changes:
            disconnectBranch(srcNode);
            connectBranchToRoot(srcNode, destNode, newTime);
            tree.setRoot(srcNodeP);
			
			// Recolour root branches, forcing reject if inconsistent:
			if (!recolourRootBranches(srcNode, newChangeCount))
				return Double.NEGATIVE_INFINITY;
			
			// Calculate HR:
			logHR = Math.log(t_destNode)
					- mu*(oldTime+t_destNode-2*newTime)
					+ alpha*(newTime/t_destNode - 1.0)
					- Math.log(alpha*(t_srcNodeG - Math.max(t_srcNode, t_srcNodeS)))
					+ (oldChangeCount-newChangeCount)*Math.log(mu/cTree.getNColours()-1);
					
            return logHR;
        }
		
		if (srcNodeP.isRoot()) {
            // BACKWARD ROOT MOVE
			
			// Record details of srcNode's sister:
			Node srcNodeS = getOtherChild(srcNodeP, srcNode);
			double t_srcNodeS = srcNodeS.getHeight();
			
			// Record old srcNode parent height and combined change count
			// for srcNode and her sister:
			double oldTime = t_srcNodeP;
			int oldChangeCount = cTree.getChangeCount(srcNode)
					+ cTree.getChangeCount(srcNodeS);
			
			// Choose height of new attachement point:
			double min_newTime = Math.max(t_srcNode, t_destNode);
            double t_destNodeP = destNodeP.getHeight();
			double span = t_destNodeP - min_newTime;
			double newTime = min_newTime + span*Randomizer.nextDouble();
			
			// Choose number of new colour changes to generate:
			double L = newTime - t_srcNode;
			int newChangeCount = PoissonRandomizer.nextInt(mu*L);

            // Implement tree changes:
			Node srcNodeSister = getOtherChild(srcNode.getParent(), srcNode);
            disconnectBranchFromRoot(srcNode);
            connectBranch(srcNode, destNode, newTime);			
            srcNodeSister.setParent(null);
            tree.setRoot(srcNodeSister);

            // Recolour new branch, rejecting outright if inconsistent:
			if (!recolourBranch(srcNode, newChangeCount))
				return Double.NEGATIVE_INFINITY;
			
			// Calculate HR:
			
			logHR = Math.log(alpha*(t_destNodeP-Math.max(t_srcNode,t_destNode)))
					- Math.log(t_srcNodeS)
					+ (oldChangeCount-newChangeCount)*Math.log(mu/cTree.getNColours()-1)
					- mu*(2*oldTime - t_srcNodeS - newTime)
					- alpha*(oldTime/t_srcNodeS - 1.0);

            return logHR;
        }

		// NON-ROOT MOVE
		
		// Record old srcNodeP height and change count:
		double oldTime = t_srcNodeP;
		int oldChangeCount = cTree.getChangeCount(srcNode);
		
		// Record srcNode sister and grandmother heights:
		double t_srcNodeS = getOtherChild(srcNodeP, srcNode).getHeight();
		double t_srcNodeG = srcNodeP.getParent().getHeight();
		
		// Choose height of new attachment point:
		double min_newTime = Math.max(t_destNode, t_srcNode);
		double t_destNodeP = destNodeP.getHeight();
		double span = t_destNodeP - min_newTime;
		double newTime = min_newTime + span*Randomizer.nextDouble();

		// Choose number of new colour changes to generate:
		double L = newTime - t_srcNode;
		int newChangeCount = PoissonRandomizer.nextInt(mu*L);

		// Implement tree changes:
		disconnectBranch(srcNode);
		connectBranch(srcNode, destNode, newTime);

		// Recolour new branch, rejecting outright if inconsistent:
		if (!recolourBranch(srcNode, newChangeCount))
			return Double.NEGATIVE_INFINITY;
		
		// Calculate HR:
		logHR = Math.log(t_destNodeP-Math.max(t_srcNode, t_destNode))
				- Math.log(t_srcNodeG-Math.max(t_srcNode, t_srcNodeS))
				- mu*(oldTime - newTime)
				+ (oldChangeCount-newChangeCount)*Math.log(mu/(cTree.getNColours()-1));

		return logHR;
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
		while (i<nChanges && times[i]<Lfirst) {
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
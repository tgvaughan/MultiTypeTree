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
package beast.evolution.operator;

import beast.core.Description;
import beast.core.Input;
import beast.evolution.tree.Node;
import beast.math.GammaFunction;
import beast.util.PoissonRandomizer;
import beast.util.Randomizer;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A subtree slide operator for coloured trees.
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("A subtree slide operator for coloured trees. ")
public class ColouredSubtreeSlide extends ColouredTreeOperator {

	public Input<Double> muInput = new Input<Double>("mu",
			"Mean rate of colour change along branch.", Input.Validate.REQUIRED);

	public Input<Double> rootSlideParamInput = new Input<Double>("rootSlideParam",
			"Root sliding is restricted to [sister.height,root.height+rootSlideParam]",
			Input.Validate.REQUIRED);

	private double mu, rootSlideParam;

	@Override
	public void initAndValidate() { }

	@Override
	public double proposal() {

		cTree = colouredTreeInput.get();
		tree = cTree.getUncolouredTree();
		mu = muInput.get();
		rootSlideParam = rootSlideParamInput.get();

		// Keep track of Hastings ratio while generating proposal:
		double logHR = 0;

		// Choose non-root node:
		Node node;
		do {
			node = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
		} while (node.isRoot());

		Node parent = node.getParent();
		Node sister = getOtherChild(parent, node);

		double t_node = node.getHeight();
		double t_parentOld = parent.getHeight();
		double t_sister = sister.getHeight();

		if (parent.isRoot()) {
			
			// Select new height of parent:
			double u = Randomizer.nextDouble();
			double f = u*rootSlideParam + (1.0-u)/rootSlideParam;
			double tMin = Math.max(t_node, t_sister);
			double t_parentNew = tMin + f*(t_parentOld-tMin);
			
			if (t_parentNew>t_parentOld) {
				// Root age INCREASE move

				// Record old number of colour change events:
				int nChangesOld = cTree.getChangeCount(node);

				// Select number of colour change events:
				double L = (t_parentNew - t_node) + (t_parentNew - t_parentOld);
				int nChanges = PoissonRandomizer.nextInt(mu*L);

				// Record forward move probability:
				logHR -= getLogMoveProb(node, t_parentNew, nChanges);
				
				// Assign new height to parent:
				parent.setHeight(t_parentNew);
				
				// Recolour branch between node, up to root, then down toward
				// sister to a height of tOld.  Reject outright if final colour
				// at the end of this path on sister is different to the original
				// colour at that point.
				if (!recolourRootBranch(node, t_parentOld, nChanges))
					return Double.NEGATIVE_INFINITY;
				
				// Record reverse move probability:
				logHR += getLogMoveProb(node, t_parentOld, nChangesOld);

			} else {
				// Root age DECREASE move:
				
				// Record old number of colour change events:
				int nChangesOld = cTree.getChangeCount(node);
				int i = cTree.getChangeCount(sister)-1;
				while (i>=0 && cTree.getChangeTime(sister, i)>t_parentOld) {
					nChangesOld += 1;
					i -= 1;
				}

				// Select number of colour change events:
				double L = t_parentNew - t_node;
				int nChanges = PoissonRandomizer.nextInt(mu*L);
				
				// Record forward move probability:
				logHR -= getLogMoveProb(node, t_parentNew, nChanges);

				// Assign new height to parent:
				parent.setHeight(t_parentNew);

				// Update colour changes between sister and root to reflect
				// change:
				removeExcessColours(getOtherChild(parent, node));

				// Recolour branch between node and root.  Reject outright if
				// final colour is different to existing colour on root node.
				if (!recolourBranch(node, nChanges)) {
					// DEBUG:
					//System.out.println("Reject.");
					return Double.NEGATIVE_INFINITY;
				}
								
				// Record reverse move probability:
				logHR += getLogMoveProb(node, t_parentOld, nChangesOld);
			}

		} else {

			// TODO: Add standard subtree slide

		}

		return logHR;
	}
	
	/**
	 * Obtain probability of a particular move of branch starting at node
	 * to join to its parent at time newParentTime and with nChanges colour
	 * changes along it.  Also handles moves in which the root node is
	 * repositioned.
	 * 
	 * @param node
	 * @param newParentTime
	 * @param nChanges
	 * @return Log of move probability.
	 */
	double getLogMoveProb(Node node, double newParentTime, int nChanges) {
		double logP = 0;
		
		Node parent = node.getParent();
		Node sister = getOtherChild(parent, node);
		
		double t_node = node.getHeight();
		double t_parentOld = parent.getHeight();
		double t_sister = sister.getHeight();
		
		double tMinHard = Math.max(node.getHeight(), sister.getHeight());
		double tMin = (t_parentOld - tMinHard)/rootSlideParam + tMinHard;
		double tMax = (t_parentOld - tMinHard)*rootSlideParam + tMinHard;
		
		// Reject outright if proposed time outside of bounds:
		if (newParentTime>tMax || newParentTime<tMin)
			return Double.NEGATIVE_INFINITY;
		
		// Probability of selecting new time:
		logP += Math.log(1.0/(tMax-tMin));
		
		// Probability of selecting nChanges colour change events:
		double L;
		if (node.getParent().isRoot() && newParentTime > t_parentOld)
			L = (newParentTime-t_node) + (newParentTime-t_parentOld);
		else
			L = newParentTime-t_node;
		logP += -mu*L + nChanges*Math.log(mu*L);
				//- GammaFunction.lnGamma(nChanges+1);
		
		// Probability of selecting a particular colour assignment:
		logP += nChanges*Math.log(1.0/(cTree.getNColours()-1));
		
		// Probability of drawing a particular sequence of ordered change times:
		logP += -(nChanges*Math.log(L)); // - GammaFunction.lnGamma(nChanges+1));
				
		return logP;
	}

	/**
	 * Recolour branch above node with nChanges random colour changes.
	 * 
	 * @param node
	 * @param nChanges 
	 * @return True if selected colour change consistent.
	 */
	private boolean recolourBranch(Node node, int nChanges) {

		// Early exit for single-coloured trees:
		if (cTree.getNColours()<2)
			return true;
		
		Node parent = node.getParent();
		double t_node = node.getHeight();
		double t_parent = parent.getHeight();
		int nodeCol = cTree.getNodeColour(node);
		int parentCol = cTree.getNodeColour(parent);
		
		// Clear current changes:
		setChangeCount(node, 0);
		
		// Early exit if no changes scheduled:
		if (nChanges==0) {
			if (nodeCol != parentCol)
				return false;
			else
				return true;
		}

		// Obtain colours and times of change events:
		double L = t_parent - t_node;
		double[] times = getTimes(L, nChanges);
		int[] colours = getColours(cTree.getNodeColour(node), nChanges);
		
		// Force reject if final colour doesn't match parent colour:
		if (colours[nChanges-1] != parentCol)
			return false;
		
		// Add newly selected colour changes to branch:
		for (int i=0; i<times.length; i++)
			addChange(node, colours[i], times[i] + t_node);

		return true;
	}

	/**
	 * Recolour branch from node to root then back down toward sister to age
	 * tOnSister with random colour changes.
	 * 
	 * @param node
	 * @param tOnSister 
	 */
	private boolean recolourRootBranch(Node node, double tOnSister,
			int nChanges) {

		// Early exit for single-coloured trees:
		if (cTree.getNColours()<2)
			return true;
		
		Node parent = node.getParent();
		Node sister = getOtherChild(parent, node);
		double t_node = node.getHeight();
		double t_parent = parent.getHeight();
		int nodeCol = cTree.getNodeColour(node);
		int finalCol = cTree.getColourOnBranch(sister, tOnSister);

		// Clear existing changes above node
		// (shouldn't be any changes above tOnSister on sister)
		setChangeCount(node, 0);
				
		// Early exit if no changes scheduled:
		if (nChanges == 0) {
			if (nodeCol != finalCol)
				return false;
			else
				return true;
		}

		// Obtain colours and times of change events:
		double L = 2*t_parent - t_node - tOnSister;
		double[] times = getTimes(L, nChanges);
		int[] colours = getColours(nodeCol, nChanges);
		
		// Force reject if chosen final colour doesn't match
		// required final colour:
		if (colours[nChanges-1] != finalCol)
			return false;
		
		// Add colours to branch above node:
		int lastCol = nodeCol;
		int i=0;
		while (i<nChanges && times[i] < t_parent-t_node) {
			addChange(node, colours[i], times[i]+t_node);
			lastCol = colours[i];
			i += 1;
		}
		
		// Set root node colour on the way past:
		setNodeColour(parent, lastCol);
		
		// Add colours to branch above sister node:
		for (int j=nChanges-1; j>=i; j--) {
			double thisTime = (L - times[j]) + tOnSister;
			int thisCol;
			if (j>0)
				thisCol = colours[j-1];
			else
				thisCol = lastCol;
			
			addChange(sister, thisCol, thisTime);
		}
		
		return true;
	}

	/**
	 * Remove colour changes on branch between node and its parent which
	 * occur at times greater than the age of the parent.
	 * 
	 * @param node 
	 */
	private void removeExcessColours(Node node) {

		double tParent = node.getParent().getHeight();

		int newChangeCount = 0;
		while (newChangeCount<cTree.getChangeCount(node) &&
			cTree.getChangeTime(node, newChangeCount)<tParent) {
			newChangeCount++;
		}

		setChangeCount(node, newChangeCount);

		// Ensure colour at parent node consistent with last change:
		setNodeColour(node.getParent(), cTree.getFinalBranchColour(node));

		// DEBUG:
		// System.out.println("After REC: " + cTree.getFlattenedTree());
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
	
}

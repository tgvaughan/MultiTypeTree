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
import beast.util.Randomizer;
import com.google.common.collect.Lists;
import java.util.ArrayList;
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

		double tOld = parent.getHeight();

		if (parent.isRoot()) {

			// Calculate probability of selecting old height:
			double tMinHard = Math.max(node.getHeight(), sister.getHeight());
			double tMin = (parent.getHeight()-tMinHard)/rootSlideParam + tMinHard;
			double tMax = (parent.getHeight()-tMinHard)*rootSlideParam + tMinHard;
			double logNewHeightProb = Math.log(1.0/(tMax-tMin));
			logHR -= logNewHeightProb;

			// Select new height of parent:
			double tNew = tMin + Randomizer.nextDouble()*(tMax-tMin);

			// Calculate probability of selecting new height:
			double tMinPrime = (tNew-tMinHard)/rootSlideParam + tMinHard;
			double tMaxPrime = (tNew-tMinHard)*rootSlideParam + tMinHard;
			double logOldHeightProb = Math.log(1.0/(tMaxPrime-tMinPrime));
			logHR += logOldHeightProb;


			if (tNew>tOld) {
				// Root age INCREASE move

				// DEBUG:
				//System.out.println("Root age INCREASE move...");
				//System.out.println("Before: " + cTree.getFlattenedTree());

				// Record probability of old path:
				double logOldProb = getBranchProb(node);
				logHR += logOldProb;

				// Assign new height to parent:
				parent.setHeight(tNew);

				// Recolour branch between node, up to root, then down toward
				// sister to a height of tOld.  Reject outright if final colour
				// at the end of this path on sister is different to the original
				// colour at that point.
				if (!recolourRootBranch(node, tOld)) {
					// DEBUG:
					//System.out.println("Reject.");
					return Double.NEGATIVE_INFINITY;
				}

				// DEBUG:
				//System.out.println("After: " + cTree.getFlattenedTree());

				// Calculate probability of new path:
				double logNewProb = getRootBranchProb(node, tOld);
				logHR -= logNewProb;

			} else {
				// Root age DECREASE move:

				// DEBUG:
				//System.out.println("Root age DECREASE move...");
				//System.out.println("Before: " + cTree.getFlattenedTree());

				// Record probability of old path:
				double logOldProb = getRootBranchProb(node, tNew);
				logHR += logOldProb;

				// Assign new height to parent:
				parent.setHeight(tNew);

				// Update colour changes between sister and root to reflect
				// change:
				removeExcessColours(getOtherChild(parent, node));

				// Recolour branch between node and root.  Reject outright if
				// final colour is different to existing colour on root node.
				if (!recolourBranch(node)) {
					// DEBUG:
					//System.out.println("Reject.");
					return Double.NEGATIVE_INFINITY;
				}

				// DEBUG:
				//System.out.println("After: " + cTree.getFlattenedTree());

				// Calculate probability of new path:
				double logNewProb = getBranchProb(node);
				logHR -= logNewProb;
			}


		} else {

			// TODO: Add standard subtree slide

		}

		return logHR;
	}

	/**
	 * Calculate probability density of a given colour assignment.
	 * 
	 * @param node
	 * @return Log probability density.
	 */
	private double getBranchProb(Node node) {

		// Special case for single colour models:
		if (cTree.getNColours()<2)
			return 0.0;

		double delta = node.getParent().getHeight() - node.getHeight();
		int n = cTree.getChangeCount(node);

		return -mu*delta + n*Math.log(mu*delta) - 2*GammaFunction.lnGamma(n+1)
				- n*Math.log(cTree.getNColours()-1);
	}


	/**
	 * Calculate probability density of colour assignment from node,
	 * up to the root, then back down to the point at height t on
	 * the branch between the root and node's sister.
	 * 
	 * @param node
	 * @param timeOnSister
	 * @return Log probability density.
	 */
	private double getRootBranchProb(Node node, double timeOnSister) {

		// Special case for single colour models:
		if (cTree.getNColours()<2)
			return 0.0;

		double delta = node.getParent().getHeight() - node.getHeight()
				+ node.getParent().getHeight() - timeOnSister;

		int n = cTree.getChangeCount(node);

		// Count changes on sister above timeOnSister:
		Node sister = getOtherChild(node.getParent(), node);
		for (int i=cTree.getChangeCount(sister)-1;
				i>=0 && cTree.getChangeTime(sister, i)>timeOnSister;
				i--) {
			n  += 1;
		}

		return -mu*delta + n*Math.log(mu*delta) - 2*GammaFunction.lnGamma(n+1)
				- n*Math.log(cTree.getNColours()-1);
	}	

	/**
	 * Recolour branch above node with random colour changes.
	 * 
	 * @param node 
	 */
	private boolean recolourBranch(Node node) {

		// Early exit for single-coloured trees:
		if (cTree.getNColours()<2)
			return true;

		// Clear current changes:
		setChangeCount(node, 0);

		double t = node.getHeight();
		double tP = node.getParent().getHeight();
		int lastCol = cTree.getNodeColour(node);

		while (t<tP) {

			t += Randomizer.nextExponential(mu);
			if (t<tP) {
				// Select next colour:
				int newCol;
				do {
					newCol = Randomizer.nextInt(cTree.getNColours());
				} while (newCol == lastCol);

				// Record change:
				addChange(node, newCol, t);

				// Update last colour:
				lastCol = newCol;
			}
		}

		if (lastCol != cTree.getNodeColour(node.getParent()))
			return false;

		return true;
	}

	/**
	 * Recolour branch from node to root then back down toward sister to age
	 * tOnSister with random colour changes.
	 * 
	 * @param node
	 * @param tOnSister 
	 */
	private boolean recolourRootBranch(Node node, double tOnSister) {

		// Early exit for single-coloured trees:
		if (cTree.getNColours()<2)
			return true;

		// Obtain reference to sister node:
		Node sister = getOtherChild(node.getParent(), node);

		// Clear branch above node:
		setChangeCount(node, 0);

		// Calculate length of path to create:
		double t = node.getHeight();
		double tRoot = node.getParent().getHeight();
		double tEnd = tRoot + (tRoot-tOnSister);

		// Set up lists to hold colour changes along second
		// part of path:
		List<Double> partTwoTimes = new ArrayList<Double>();
		List<Integer> partTwoColours = new ArrayList<Integer>();

		int lastCol = cTree.getNodeColour(node);

		while (true) {

			// Sample length of next increment:
			double lastTime = t;
			t += Randomizer.nextExponential(mu);

			// Check for end of path:
			if (t>tEnd)
				break;

			// select next colour:
			int newCol;
			do {
				newCol = Randomizer.nextInt(cTree.getNColours());
			} while (newCol == lastCol);

			if (t<tRoot) {

				// Record change:
				addChange(node, newCol, t);

			} else {

				// Set root node colour if necessary:
				if (lastTime < tRoot)
					setNodeColour(node.getParent(), lastCol);

				partTwoTimes.add(tRoot-(t-tRoot));
				partTwoColours.add(lastCol);
			}

			// Update last colour:
			lastCol = newCol;
		}

		if (lastCol != cTree.getColourOnBranch(sister, tOnSister))
			return false;

		// Read change colours and times in direction from sister to root:
		partTwoTimes = Lists.reverse(partTwoTimes);
		partTwoColours = Lists.reverse(partTwoColours);

		// Append new colour changes to sister branch:
		for (int i=0; i<partTwoTimes.size(); i++)
			addChange(sister, partTwoColours.get(i), partTwoTimes.get(i));

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
	
}

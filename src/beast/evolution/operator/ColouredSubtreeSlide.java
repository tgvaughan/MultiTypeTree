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
		tree = m_tree.get();
		mu = muInput.get();
		rootSlideParam = rootSlideParamInput.get();

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


			if (tNew>parent.getHeight()) {
				// Root age INCREASE move

				// Record probability of old path:
				double logOldProb = getBranchProb(node, 50);
				logHR += logOldProb;

				// Assign new height to parent:
				parent.setHeight(tNew);

				// Recolour branch between node, up to root, then down toward
				// sister to a height of tOld.
				recolourRootBranch(node, tOld);

				// Calculate probability of new path:
				double logNewProb = getRootBranchProb(node, tOld, 50);
				logHR -= logNewProb;

			} else {
				// Root age DECREASE move:

				// Record probability of old path:
				double logOldProb = getRootBranchProb(node, tNew, 50);
				logHR += logOldProb;

				// Assign new height to parent:
				parent.setHeight(tNew);

				// Update colour changes between sister and root to reflect
				// change:
				removeExcessColours(node);

				// Recolour branch between node and root:
				recolourBranch(node);

				// Calculate probability of new path:
				double logNewProb = getBranchProb(node, 50);
				logHR -= logNewProb;
			}


		} else {

			// TODO: Add standard subtree slide

		}

		return logHR;
	}

	/**
	 * Estimate probability of particular colour arrangement using a finite
	 * time step approximation.  Used for testing analytical expression for
	 * acceptance probability.
	 * 
	 * @param node
	 * @param integrationSteps
	 * @return 
	 */
	private double getBranchProb(Node node, int integrationSteps) {

		double ti = node.getHeight();
		double tf = node.getParent().getHeight();
		double dt = (tf-ti)/(integrationSteps-1);

		double logP = 0;
		int lastColour = cTree.getNodeColour(node);

		for (int step=1; step<integrationSteps; step++) {

			int thisColour = cTree.getColourOnBranch(node, dt*step);

			if (thisColour != lastColour) {
				logP += Math.log(dt*mu/(cTree.getNColours()-1));
				lastColour = thisColour;
			} else
				logP += Math.log(1-dt*mu);
		}

		return logP;
	}


	/**
	 * Estimate probability of particular colour arrangement beginning at
	 * node, up to the root, then back down to the point at height t on
	 * the branch between the root and node's sister.  Uses finite time
	 * step approximation.  For testing analytical expression for acceptance
	 * probability.
	 * 
	 * @param node
	 * @param t
	 * @param integrationSteps
	 * @return 
	 */
	private double getRootBranchProb(Node node, double timeOnSister,
			int integrationSteps) {

		double tNode = node.getHeight();
		double tRoot = node.getParent().getHeight();
		double tFull = (tRoot-tNode) + (tRoot-timeOnSister);
		double dt = tFull/(integrationSteps-1);

		double logP = 0;
		int lastColour = cTree.getNodeColour(node);

		for (int step=1; step<integrationSteps; step++) {

			double t = dt*step + tNode;

			if (t > tRoot)
				t = tRoot - t;

			int thisColour = cTree.getColourOnBranch(node, t);

			if (thisColour != lastColour) {
				logP += Math.log(dt*mu/(cTree.getNColours()-1));
				lastColour = thisColour;
			} else
				logP += Math.log(1-dt*mu);
		}

		return logP;
	}	

	/**
	 * Recolour branch above node with random colour changes.
	 * 
	 * @param node 
	 */
	private void recolourBranch(Node node) {

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
	}

	/**
	 * Recolour branch from node to root then back down toward sister to age
	 * tOnSister with random colour changes.
	 * 
	 * @param node
	 * @param tOnSister 
	 */
	private void recolourRootBranch(Node node, double tOnSister) {

		// Obtain reference to sister node:
		Node sister = getOtherChild(node, node.getParent());

		// Clear branch above node:
		setChangeCount(node, 0);

		// Calculate length of path to create:
		double t = node.getHeight();
		double tRoot = node.getParent().getHeight();
		double tVirtual = tRoot + (tRoot-t);

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
			if (t>tVirtual)
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

		// Read change colours and times in direction from sister to root:
		partTwoTimes = Lists.reverse(partTwoTimes);
		partTwoColours = Lists.reverse(partTwoColours);

		// Append new colour changes to sister branch:
		for (int i=0; i<partTwoTimes.size(); i++)
			addChange(sister, partTwoColours.get(i), partTwoTimes.get(i));
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
	}
	
}

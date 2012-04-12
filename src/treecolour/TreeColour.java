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
package treecolour;

import beast.core.*;
import beast.core.parameter.*;
import beast.evolution.tree.*;

/**
 * BEAST 2 plugin for specifying migration events along a tree.
 *
 * @author Tim Vaughan
 */
@Description("Plugin for specifying migration events along a tree.")
public class TreeColour extends Plugin {

	/*
	 * Plugin inputs:
	 */

	Input<Integer> nColoursInput = new Input<Integer>(
			"nColours", "Number of demes to consider.");

	Input<Integer> maxBranchColoursInput = new Input<Integer>(
			"maxChangesPerBranch",
			"Max number of colour changes allowed along a single branch.");

	Input<Tree> treeInput = new Input<Tree>(
			"tree", "Tree on which to place colours.");

	Input<IntegerParameter> leafColoursInput = new Input<IntegerParameter>(
			"leafColours", "Sampled colours at tree leaves.");

	Input<IntegerParameter> changeColoursInput = new Input<IntegerParameter>(
			"changeColours", "Changes in colour along branches",
			new IntegerParameter());

	Input<RealParameter> changeTimesInput = new Input<RealParameter>(
			"changeTimes", "Times of colour changes.");

	Input<IntegerParameter> changeCountsInput = new Input<IntegerParameter>(
			"changeCounts", "Number of colour changes on each branch.");

	/*
	 * Shadowing fields:
	 */

	Integer nColours, maxBranchColours;
	Tree tree;
	IntegerParameter leafColours, changeColours, changeCounts;
	RealParameter changeTimes;

	public TreeColour() {};

	@Override
	public void initAndValidate() throws Exception {

		// Grab primary colouring parameters from inputs:
		nColours = nColoursInput.get();
		maxBranchColours = maxBranchColoursInput.get();

		// Obtain tree to colour:
		tree = treeInput.get();

		// Obtain leaf colours and validate count:
		leafColours = leafColoursInput.get();
		if (tree.getLeafNodeCount() != leafColours.getDimension())
			throw new Exception("Incorrect number of leaf colours specified.");

		// Obtain references to Parameters used to store colouring:
		changeColours = changeColoursInput.get();
		changeTimes = changeTimesInput.get();
		changeCounts = changeCountsInput.get();

		// Initialise colouring:
		int nBranches = tree.getNodeCount()-1;
		changeColours.setDimension(nBranches*maxBranchColours);
		changeTimes.setDimension(nBranches*maxBranchColours);
		changeCounts.setDimension(nBranches);

	}

	/**
	 * Obtain colour associated with leaf node of tree.
	 * 
	 * @param node
	 * @return Colour index.
	 */
	public int getLeafColour(Node node) {
		return leafColours.getValue(node.getNr());
	}

	/**
	 * Set number of colours on branch between node and its parent.
	 * 
	 * @param node
	 * @param count Number of colours on branch. (>=1)
	 */
	public void setChangeCount(Node node, int count) {
		int offset = getBranchOffset(node);
		changeCounts.setValue(offset, count);
	}

	/**
	 * Obtain number of colours on branch between node and its parent.
	 * 
	 * @param node
	 * @return Number of colours on branch.
	 */
	public int getChangeCount(Node node) {
		return changeCounts.getValue(getBranchOffset(node));
	}

	/**
	 * Sets colour changes along branch between node and its parent.
	 * 
	 * @param node
	 * @param colours Vararg list of colour indices.
	 */
	public void setChangeColours(Node node, int ... colours) {

		if (colours.length>maxBranchColours)
			throw new RuntimeException(
					"Maximum number of colour changes along branch exceeded");

		int offset = node.getNr()*maxBranchColours;
		for (int i=0; i<colours.length; i++)
			changeColours.setValue(offset+i, colours[i]);

	}

	/**
	 * Sets times of colour changes long branch between node and its parent.
	 * 
	 * @param node
	 * @param times Vararg list of colour change times.
	 */
	public void setChangeTimes(Node node, double ... times) {

		if (times.length>maxBranchColours)
			throw new RuntimeException(
					"Maximum number of colour changes along branch exceeded");

		int offset = getBranchOffset(node);
		for (int i=0; i<times.length; i++)
			changeTimes.setValue(offset+i, times[i]);
	}

	public int getChangeColour(Node node, int idx) {
		if (idx>getChangeCount(node))
			throw new RuntimeException(
					"Index to getChangeColour exceeded total number of colour"
					+ "changes on branch.");

		return changeColours.getValue(getBranchOffset(node)+idx);
	}

	public double getChangeTime(Node node, int idx) {
		if (idx>getChangeCount(node))
			throw new RuntimeException(
					"Index to getChangeTime exceeded total number of colour"
					+ "changes on branch.");

		return changeTimes.getValue(idx);
	}

	/**
	 * Internal method for calculating offset to branch-specific colour data.
	 * 
	 * @param node
	 * @return Offset into changeColours and changeTimes
	 */
	private int getBranchOffset(Node node) {
		return node.getNr()*maxBranchColours;
	}

	/**
	 * Checks validity of current colour assignment.
	 * 
	 * @return True if valid, false otherwise.
	 */
	public boolean valid() {
		return coloursValid(tree.getRoot());
	}

	/**
	 * Returns the final colour after all the changes have occurred
	 * along the branch between node and its parent.
	 * 
	 * @param node
	 * @return Final colour.
	 */
	private int getFinalColour(Node node) {
		if (getChangeCount(node) == 0) {
			if (node.isLeaf()) {
				return getLeafColour(node);
			} else
				return getFinalColour(node.getLeft());
		} else
			return getChangeColour(node, getChangeCount(node)-1);
	}

	/**
	 * Recursively check validity of colours assigned to branches of subtree
	 * under node.
	 * 
	 * @param node Root node of subtree to validate.
	 * @return True if valid, false otherwise.
	 */
	private boolean coloursValid(Node node) {

		// Leaves are always valid.
		if (node.isLeaf())
			return true;

		int consensusColour = -1;
		for (Node child : node.getChildren()) {
			
			// Check that final child branch colour matches consensus:
			int thisColour = getFinalColour(child);
			if (consensusColour<0)
				consensusColour = thisColour;
			else {
				if (thisColour != consensusColour)
					return false;
			}

			// Check that subtree of child is also valid:
			if (!child.isLeaf() && !coloursValid(child))
				return false;
		}

		return true;
	}

	/**
	 * Recursively check validity of times at which colours are assigned
	 * to branches of subtree under node.
	 * 
	 * @param node
	 * @return  True if valid, false otherwise.
	 */
	private boolean timesValid(Node node) {

		return true;
	}

}
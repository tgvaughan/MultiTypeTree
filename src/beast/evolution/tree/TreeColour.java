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
package beast.evolution.tree;

import beast.core.*;
import beast.core.parameter.*;

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

	public Input<String> colourLabelInput = new Input<String>(
			"colourLabel", "Label for colours (e.g. deme)");

	public Input<Integer> nColoursInput = new Input<Integer>(
			"nColours", "Number of colours to consider.");

	public Input<Integer> maxBranchColoursInput = new Input<Integer>(
			"maxBranchColours",
			"Max number of colour changes allowed along a single branch.");

	public Input<Tree> treeInput = new Input<Tree>(
			"tree", "Tree on which to place colours.");

	public Input<IntegerParameter> leafColoursInput = new Input<IntegerParameter>(
			"leafColours", "Sampled colours at tree leaves.");

	public Input<IntegerParameter> changeColoursInput = new Input<IntegerParameter>(
			"changeColours", "Changes in colour along branches");

	public Input<RealParameter> changeTimesInput = new Input<RealParameter>(
			"changeTimes", "Times of colour changes.");

	public Input<IntegerParameter> changeCountsInput = new Input<IntegerParameter>(
			"changeCounts", "Number of colour changes on each branch.");

	/*
	 * Shadowing fields:
	 */

	protected String colourLabel;
	protected Integer nColours, maxBranchColours;
	protected Tree tree;
	protected IntegerParameter leafColours, changeColours, changeCounts;
	protected RealParameter changeTimes;

	public TreeColour() {};

	@Override
	public void initAndValidate() throws Exception {

		// Grab primary colouring parameters from inputs:
		colourLabel = colourLabelInput.get();
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

		// Allocate arrays for recording colour change information:
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
		changeCounts.setValue(node.getNr(), count);
	}

	/**
	 * Obtain number of colours on branch between node and its parent.
	 * 
	 * @param node
	 * @return Number of colours on branch.
	 */
	public int getChangeCount(Node node) {
		return changeCounts.getValue(node.getNr());
	}

	/**
	 * Set new colour for change which has already been recorded.
	 * 
	 * @param node
	 * @param idx
	 * @param colour 
	 */
	public void setChangeColour(Node node, int idx, int colour) {

		if (idx>getChangeCount(node))
			throw new RuntimeException(
					"Attempted to alter non-existent change colour.");

		int offset = node.getNr()*maxBranchColours;
		changeColours.setValue(offset+idx, colour);
	}

	/**
	 * Sets colour changes along branch between node and its parent.
	 * 
	 * @param node
	 * @param colours Vararg list of colour indices.
	 */
	public void setChangeColours(Node node, int ... colours) {

		if (colours.length>maxBranchColours)
			throw new IllegalArgumentException(
					"Maximum number of colour changes along branch exceeded.");

		int offset = node.getNr()*maxBranchColours;
		for (int i=0; i<colours.length; i++)
			changeColours.setValue(offset+i, colours[i]);

	}

	/**
	 * Set new time for change which has already been recorded.
	 * 
	 * @param node
	 * @param idx
	 * @param time 
	 */
	public void setChangeTime(Node node, int idx, double time) {

		if (idx>getChangeCount(node))
			throw new IllegalArgumentException(
					"Attempted to alter non-existent change time.");

		int offset = node.getNr()*maxBranchColours;
		changeTimes.setValue(offset+idx, time);
	}

	/**
	 * Sets times of colour changes along branch between node and its parent.
	 * 
	 * @param node
	 * @param times Vararg list of colour change times.
	 */
	public void setChangeTimes(Node node, double ... times) {

		if (times.length>maxBranchColours)
			throw new IllegalArgumentException(
					"Maximum number of colour changes along branch exceeded.");

		int offset = getBranchOffset(node);
		for (int i=0; i<times.length; i++)
			changeTimes.setValue(offset+i, times[i]);
	}

	/**
	 * Add a colour change to a branch between node and its parent.
	 * @param node
	 * @param newColour
	 * @param time 
	 */
	public void addChange(Node node, int newColour, double time) {
		int count = getChangeCount(node);

		if (count>=maxBranchColours)
			throw new RuntimeException(
					"Maximum number of colour changes along branch exceeded.");

		// Add spot for new colour change:
		setChangeCount(node, count+1);

		// Set change colour and time:
		setChangeColour(node, count, newColour);
		setChangeTime(node, count, time);

	}

	/**
	 * Obtain colour of the change specified by idx on the branch between
	 * node and its parent.
	 * 
	 * @param node
	 * @param idx
	 * @return Integer value of colour.
	 */
	public int getChangeColour(Node node, int idx) {
		if (idx>getChangeCount(node))
			throw new RuntimeException(
					"Index to getChangeColour exceeded total number of colour"
					+ "changes on branch.");

		return changeColours.getValue(getBranchOffset(node)+idx);
	}

	/**
	 * Obtain time of the change specified by idx on the branch between
	 * node and its parent.
	 * 
	 * @param node
	 * @param idx
	 * @return Time of change.
	 */
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
		return coloursValid(tree.getRoot()) && timesValid(tree.getRoot());
	}

	/**
	 * Returns the final colour after all the changes have occurred
	 * along the branch between node and its parent.
	 * 
	 * @param node
	 * @return Final colour.
	 */
	int getFinalBranchColour(Node node) {
		if (getChangeCount(node) == 0) {
			if (node.isLeaf()) {
				return getLeafColour(node);
			} else
				return getFinalBranchColour(node.getLeft());
		} else
			return getChangeColour(node, getChangeCount(node)-1);
	}

	/**
	 * Returns the initial colour before any changes have occurred
	 * along the branch between node and its parent.
	 * 
	 * @param node
	 * @return Initial colour.
	 */
	int getInitialBranchColour(Node node) {
		if (node.isLeaf())
			return getLeafColour(node);
		else
			return getFinalBranchColour(node.getLeft());
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
			int thisColour = getFinalBranchColour(child);
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

		if (!node.isRoot()) {
			// Check consistency of times on branch between node
			// and its parent:
			for (int i=0; i<getChangeCount(node); i++) {
				double thisTime = getChangeTime(node, i);

				double prevTime;
				if (i>0)
					prevTime = getChangeTime(node,i-1);
				else
					prevTime = node.getHeight();

				if (thisTime<prevTime)
					return false;

				double nextTime;
				if (i<getChangeCount(node)-1)
					nextTime = getChangeTime(node,i+1);
				else
					nextTime = node.getParent().getHeight();

				if (nextTime<thisTime)
					return false;
			}
		}

		if (!node.isLeaf()) {
			// Check consistency of branches between node and
			// each of its children:
			for (Node child : node.getChildren()) {
				if (!timesValid(child))
					return false;
			}
		}
			
		return true;
	}

	/**
	 * Generates a new tree in which the colours along the branches are
	 * indicated by the traits of single-child nodes.
	 * 
	 * This method is useful for interfacing trees coloured externally
	 * using the a TreeColour instance with methods designed to act on
	 * trees coloured using single-child nodes and their metadata fields.
	 * 
	 * @return Flattened tree.
	 */
	public Tree getFlattenedTree() {

		// Create new tree to modify.  Note that copy() doesn't
		// initialise the node array lists, so initArrays() must
		// be called manually.
		Tree flatTree = tree.copy();
		flatTree.initArrays();

		int nextNodeNr = flatTree.getNodeCount();

		for (Node node : tree.getNodesAsArray()) {

			int nodeNum = node.getNr();

			if (node.isRoot()) {
				flatTree.getNode(nodeNum).setMetaData(colourLabel,
						getInitialBranchColour(node));
				continue;
			}

			Node branchNode = flatTree.getNode(nodeNum);
			branchNode.setMetaData(colourLabel,
					getInitialBranchColour(node));

			for (int i=0; i<getChangeCount(node); i++) {

				// Create and label new node:
				Node colourChangeNode = new Node();
				colourChangeNode.setNr(nextNodeNr);
				colourChangeNode.setID(String.valueOf(nextNodeNr));
				nextNodeNr++;

				// Connect to child and parent:
				branchNode.setParent(colourChangeNode);
				colourChangeNode.addChild(branchNode);

				// Ensure height and colour trait are set:
				colourChangeNode.setHeight(getChangeTime(node, i));
				colourChangeNode.setMetaData(colourLabel,
						getChangeColour(node, i));

				// Update branchNode:
				branchNode = colourChangeNode;
			}

			int parentNum = node.getParent().getNr();
			branchNode.setParent(flatTree.getNode(parentNum));

		}

		return flatTree;
	}

}
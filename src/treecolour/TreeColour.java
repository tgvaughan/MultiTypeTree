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

	Input<IntegerParameter> colourChangesInput = new Input<IntegerParameter>(
			"colourChanges", "Changes in colour along branches",
			new IntegerParameter());

	Input<RealParameter> colourChangeTimesInput = new Input<RealParameter>(
			"colourChangeTimes", "Times of colour changes.");

	Input<IntegerParameter> nColourChangesInput = new Input<IntegerParameter>(
			"nColourChanges", "Number of colour changes on each branch.");

	/*
	 * Shadowing fields:
	 */

	Integer nColours, maxBranchColours;
	Tree tree;
	IntegerParameter leafColours, colourChanges, nColourChanges;
	RealParameter colourChangeTimes;

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
		colourChanges = colourChangesInput.get();
		colourChangeTimes = colourChangeTimesInput.get();
		nColourChanges = nColourChangesInput.get();

		// Initialise colouring:
		int nBranches = tree.getNodeCount()-1;
		colourChanges.setDimension(nBranches*maxBranchColours);
		colourChangeTimes.setDimension(nBranches*maxBranchColours);
		nColourChanges.setDimension(nBranches);

	}


	/**
	 * Sets colour changes along branch between node and its parent.
	 * 
	 * @param node
	 * @param colours Vararg list of colour indices.
	 */
	public void setBranchChanges(Node node, int ... colours) {

		if (colours.length>maxBranchColours)
			throw new RuntimeException("Maximum number of colour changes"
					+ "along branch exceeded");

		int offset = node.getNr()*maxBranchColours;
		for (int i=0; i<colours.length; i++)
			colourChanges.setValue(offset+i, colours[i]);
	}

	/**
	 * Sets times of colour changes long branch between node and its parent.
	 * 
	 * @param node
	 * @param times Vararg list of colour change times.
	 */
	public void setBranchChangeTimes(Node node, double ... times) {

		if (times.length>maxBranchColours)
			throw new RuntimeException("Maximum number of colour changes"
					+ "along branch exceeded");

		int offset = getBranchOffset(node);
		for (int i=0; i<times.length; i++)
			colourChangeTimes.setValue(offset+i, times[i]);
	}

	/**
	 * Internal method for calculating offset to branch-specific colour data.
	 * 
	 * @param node
	 * @return Offset into colourChanges and colourChangeTimes
	 */
	private int getBranchOffset(Node node) {
		return node.getNr()*maxBranchColours;
	}

	/*
	public int getColour(Node node, double time) throws Exception {
		int nodeNr = node.getNr();
		double nodeTime = node.getHeight();
		double nodeParentTime = node.getParent().getHeight();
		if (time < nodeTime || time > nodeParentTime)
			throw new Exception("Specified time is not on branch.");
	}
	*/

	/**
	 * Checks validity of current colour assignment.
	 * 
	 * @return True if valid, false otherwise.
	 */
	public boolean coloursValid() {

		return true;
	}

	private boolean colourChangesValid() {

		for (Node node : tree.getNodesAsArray()) {
			
		}

		return true;
	}

}


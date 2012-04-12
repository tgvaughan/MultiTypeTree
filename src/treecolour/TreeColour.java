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

	Input<Integer> nColoursInput = new Input<Integer>(
			"nColours", "Number of demes to consider.");

	Input<Integer> maxChangesPerBranchInput = new Input<Integer>(
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

	Integer nColours, maxChangesPerBranch;
	Tree tree;
	IntegerParameter leafColours, colourChanges;
	RealParameter colourChangeTimes;

	public TreeColour() {};

	@Override
	public void initAndValidate() {

		nColours = nColoursInput.get();
		maxChangesPerBranch = maxChangesPerBranchInput.get();

		tree = treeInput.get();
		leafColours = leafColoursInput.get();

		colourChanges = colourChangesInput.get();
		colourChangeTimes = colourChangeTimesInput.get();

	}

}


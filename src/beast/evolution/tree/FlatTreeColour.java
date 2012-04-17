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

/**
 * A plugin useful for interpreting TreeColour objects as standard BEAST
 * tree objects, on which single-child nodes are used to represent each
 * colour change.
 *
 * @author Tim Vaughan
 */
@Description("A standard BEAST tree representation of a TreeColour object.")
public class FlatTreeColour extends Tree {

	public Input<TreeColour> treeColourInput = new Input<TreeColour>(
			"treeColour", "Coloured tree to flatten.");

	protected TreeColour treeColour;

	public FlatTreeColour() {};

	@Override
	public void initAndValidate() {
		treeColour = treeColourInput.get();
		setRoot(treeColour.getFlattenedTree().getRoot());
	}
	
}

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
import beast.core.Operator;
import beast.evolution.tree.ColouredTree;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;

/**
 *
 * @author Tim Vaughan
 */
@Description("This operator changes a coloured beast tree.")
abstract public class ColouredTreeOperator extends Operator {

	public Input<ColouredTree> colouredTreeInput = new Input<ColouredTree>(
			"colouredTree", "Coloured tree on which to operate.",
			Validate.REQUIRED);

	/**
	 * Return sister of given child.
	 * 
	 * @param parent
	 * @param child
	 * @return Sister node of child.
	 */
	protected Node getOtherChild(Node parent, Node child) {
		if (parent.getLeft().getNr()==child.getNr())
			return parent.getRight();
		else
			return parent.getLeft();
	}

	 /**
     * Replace child with another node
     *
     * @param node
     * @param child
     * @param replacement
     */
    public void replace(Node node, Node child, Node replacement) {
    	node.getChildren().remove(child);
    	node.getChildren().add(replacement);

        node.makeDirty(Tree.IS_FILTHY);
        replacement.setParent(node);
        replacement.makeDirty(Tree.IS_FILTHY);
    }
	
}

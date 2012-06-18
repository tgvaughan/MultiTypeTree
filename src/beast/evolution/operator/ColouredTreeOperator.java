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
@Description("This operator generates proposals for a coloured beast tree.")
abstract public class ColouredTreeOperator extends Operator {

	public Input<ColouredTree> colouredTreeInput = new Input<ColouredTree>(
			"colouredTree", "Coloured tree on which to operate.",
			Validate.REQUIRED);

	protected Tree tree;
	protected ColouredTree cTree;

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

	/**
	 * Disconnect edge <node,node.getParent()> by joining node's sister
	 * directly to node's grandmother and adding all colour changes previously
	 * on <node.getParent(),node.getParent().getParent()> to the new branch.
	 * 
	 * @param node 
	 */
	public void disconnectBranch(Node node) {

		// Check argument validity:
		Node parent = node.getParent();
		if (node.isRoot() || parent.isRoot()) {
			throw new IllegalArgumentException("Illegal argument to "
					+ "disconnectBranch().");
		}

		Node sister = getOtherChild(parent, node);

		// Add colour changes originally attached to parent to those attached
		// to node's sister:
		for (int idx = 0; idx<cTree.getChangeCount(parent); idx++) {
			int colour = cTree.getChangeColour(parent, idx);
			double time = cTree.getChangeTime(parent, idx);
			cTree.addChange(sister, colour, time);
		}

		// Implement topology change.
		replace(parent.getParent(), parent, sister);
	}

	/**
	 * Disconnect node from root, discarding all colouring
	 * on <node,root> and <node's sister,root>.
	 * 
	 * @param node 
	 */
	public void disconnectBranchFromRoot(Node node) {

		// Check argument validity:
		if (node.isRoot() || !node.getParent().isRoot())
			throw new IllegalArgumentException("Illegal argument to"
					+ " disconnectBranchFromRoot().");

		// Implement topology change:
		Node parent = node.getParent();
		Node sister = getOtherChild(parent, node);
		sister.setParent(null);
		parent.getChildren().remove(sister);

	}

	/**
	 * Creates a new branch between node and a new node at time destTime
	 * between destBranchBase and its parent.  Colour changes are divided
	 * between the two new branches created by the split.
	 * @param node
	 * @param destBranchBase
	 * @param destTime 
	 */
	public void connectBranch(Node node, Node destBranchBase, double destTime) {

		// Check argument validity:
		if (node.isRoot() || destBranchBase.isRoot()) {
			throw new IllegalArgumentException("Illegal argument to "
					+ "connectBranch().");
		}

		// Obtain existing parent of node and set new time:
		Node parent = node.getParent();
		parent.setHeight(destTime);

		// Determine where the split comes in the list of colour changes
		// attached to destBranchBase:
		int split;
		for (split=0; split<cTree.getChangeCount(destBranchBase); split++) {
			if (cTree.getChangeTime(destBranchBase,split)>destTime)
				break;
		}

		// Divide colour changes between new branches:
		cTree.setChangeCount(parent, 0);
		for (int idx=split; idx<cTree.getChangeCount(destBranchBase); idx++) {
			cTree.addChange(parent, idx-split, destTime);
		}
		cTree.setChangeCount(destBranchBase, split);

		// Set colour at split:
		cTree.setNodeColour(parent, cTree.getFinalBranchColour(destBranchBase));

		// Implement topology changes:
		replace(destBranchBase.getParent(), destBranchBase, parent);
		destBranchBase.setParent(parent);

		if (parent.getLeft() == node)
			parent.setRight(destBranchBase);
		else
			parent.setLeft(destBranchBase);
	}

	/**
	 * Create a new branch between node and a new root node at destTime,
	 * making oldRoot the sister of node.
	 * 
	 * @param node
	 * @param oldRoot
	 * @param destTime 
	 */
	public void connectBranchToRoot(Node node, Node oldRoot, double destTime) {

		// Check argument validity:
		if (node.isRoot() || !oldRoot.isRoot())
			throw new IllegalArgumentException("Illegal argument "
					+ "to connectBranchToRoot().");

		// Obtain existing parent of node and set new time:
		Node parent = node.getParent();
		parent.setHeight(destTime);

		// Implement topology changes:
		parent.addChild(oldRoot);

	}
	
}
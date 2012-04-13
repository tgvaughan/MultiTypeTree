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

import beast.evolution.tree.*;

/**
 * Some utility methods for dealing with coloured trees.
 *
 * @author Tim Vaughan
 */
public class Utils {

	/**
	 * Takes as input a tree and a colouring scheme over that tree then
	 * generates a new tree in which the colours along the branches are
	 * indicated by the traits of single-child nodes.
	 * 
	 * @param tree
	 * @param treeColour
	 * @return Flattened tree.
	 */
	public static Tree flattenTree(Tree tree, TreeColour treeColour) {
		Tree flatTree = tree.copy();

		for (Node node : tree.getNodesAsArray()) {

			if (node.isRoot())
				continue;

			int nodeNum = node.getNr();
			int parentNum = node.getParent().getNr();

			Node branchNode = flatTree.getNode(nodeNum);

			for (int i=0; i<treeColour.getChangeCount(node); i++) {
				Node colourChangeNode = new Node();
				branchNode.setParent(colourChangeNode);
				colourChangeNode.addChild(branchNode);
				branchNode = colourChangeNode;
			}

			branchNode.setParent(flatTree.getNode(parentNum));

		}

		return flatTree;
	}
	
}

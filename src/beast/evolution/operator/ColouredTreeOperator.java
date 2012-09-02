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
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.ColouredTree;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.evolution.operators.TreeOperator;

/**
 *
 * @author Tim Vaughan
 */
@Description("This operator generates proposals for a coloured beast tree.")
abstract public class ColouredTreeOperator extends TreeOperator {

    public Input<ColouredTree> colouredTreeInput = new Input<ColouredTree>(
            "colouredTree", "Coloured tree on which to operate.",
            Validate.REQUIRED);

    public Input<IntegerParameter> changeColoursInput = new Input<IntegerParameter>(
            "changeColours", "Changes in colour along branches");

    public Input<RealParameter> changeTimesInput = new Input<RealParameter>(
            "changeTimes", "Times of colour changes.");

    public Input<IntegerParameter> changeCountsInput = new Input<IntegerParameter>(
            "changeCounts", "Number of colour changes on each branch.");

	public Input<IntegerParameter> nodeColoursInput = new Input<IntegerParameter>(
			"nodeColours", "Colour at each node (including internal nodes).");


    protected Tree tree;
    protected ColouredTree cTree;

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
            addChange(sister, colour, time);
        }

        // Implement topology change.
        replace(parent.getParent(), parent, sister);
		
		// Clear colour changes from parent:
		setChangeCount(parent, 0);
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
		
		// Clear colour changes on new root:
		setChangeCount(sister, 0);
		
		// Ensure BEAST knows to update affected likelihoods:
		parent.makeDirty(Tree.IS_FILTHY);
		sister.makeDirty(Tree.IS_FILTHY);
		node.makeDirty(Tree.IS_FILTHY);

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
        setChangeCount(parent, 0);
        for (int idx=split; idx<cTree.getChangeCount(destBranchBase); idx++) {
            addChange(parent, cTree.getChangeColour(destBranchBase,idx),
					cTree.getChangeTime(destBranchBase,idx));
        }
        setChangeCount(destBranchBase, split);

        // Set colour at split:
        setNodeColour(parent, cTree.getFinalBranchColour(destBranchBase));

        // Implement topology changes:
		
        replace(destBranchBase.getParent(), destBranchBase, parent);
        destBranchBase.setParent(parent);

        if (parent.getLeft() == node)
            parent.setRight(destBranchBase);
        else if (parent.getRight() == node)
            parent.setLeft(destBranchBase);
		
		// Ensure BEAST knows to update affected likelihoods:
		node.makeDirty(Tree.IS_FILTHY);
		parent.makeDirty(Tree.IS_FILTHY);
		destBranchBase.makeDirty(Tree.IS_FILTHY);
    }

	/**
	 * Set up node's parent as the new root with a height of destTime,
	 * with oldRoot as node's new sister.
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
        Node newRoot = node.getParent();
        newRoot.setHeight(destTime);
		
		// Implement topology changes:
        
		newRoot.setParent(null);

		if (newRoot.getLeft() == node) {
            newRoot.setRight(oldRoot);
        }
        else if (newRoot.getRight() == node)
            newRoot.setLeft(oldRoot);

        oldRoot.setParent(newRoot);
        
		// Ensure BEAST knows to recalculate affected likelihood:
		
        newRoot.makeDirty(Tree.IS_FILTHY);
        oldRoot.makeDirty(Tree.IS_FILTHY);
        node.makeDirty(Tree.IS_FILTHY);

    }
	
    /**
     * Set new colour for change which has already been recorded.
     *
     * @param node
     * @param idx
     * @param colour
     */
    public void setChangeColour(Node node, int idx, int colour) {

        if (idx>cTree.getChangeCount(node))
            throw new RuntimeException(
                    "Attempted to alter non-existent change colour.");

        int offset = node.getNr()*cTree.getMaxBranchColours();
//        cTree.changeColoursInput.get().setValue(offset+idx, colour);
        changeColoursInput.get().setValue(offset+idx, colour);

    }

    /**
     * Sets colour changes along branch between node and its parent.
     *
     * @param node
     * @param colours Vararg list of colour indices.
     */
    public void setChangeColours(Node node, int ... colours) {

        if (colours.length>cTree.getMaxBranchColours())
            throw new IllegalArgumentException(
                    "Maximum number of colour changes along branch exceeded.");

        int offset = node.getNr()*cTree.getMaxBranchColours();
        for (int i=0; i<colours.length; i++)
//            cTree.changeColoursInput.get().setValue(offset+i, colours[i]);
            changeColoursInput.get().setValue(offset+i, colours[i]);

    }

	 /**
     * Set new time for change which has already been recorded.
     *
     * @param node
     * @param idx
     * @param time
     */
    public void setChangeTime(Node node, int idx, double time) {

        if (idx>cTree.getChangeCount(node))
            throw new IllegalArgumentException(
                    "Attempted to alter non-existent change time.");

        int offset = node.getNr()*cTree.getMaxBranchColours();
//         cTree.changeTimesInput.get().setValue(offset+idx, time);
         changeTimesInput.get().setValue(offset+idx, time);
    }

    /**
     * Sets times of colour changes along branch between node and its parent.
     *
     * @param node
     * @param times Vararg list of colour change times.
     */
    public void setChangeTimes(Node node, double ... times) {

        if (times.length>cTree.getMaxBranchColours())
            throw new IllegalArgumentException(
                    "Maximum number of colour changes along branch exceeded.");

        int offset = cTree.getBranchOffset(node);
        for (int i=0; i<times.length; i++)
//            cTree.changeTimesInput.get().setValue(offset+i, times[i]);
            changeTimesInput.get().setValue(offset+i, times[i]);
    }
	
    /**
     * Set number of colours on branch between node and its parent.
     *
     * @param node
     * @param count Number of colours on branch. (>=1)
     */
    public void setChangeCount(Node node, int count) {
//        cTree.changeCountsInput.get().setValue(node.getNr(), count);
        changeCountsInput.get().setValue(node.getNr(), count);
    }

    /**
     * Add a colour change to a branch between node and its parent.
     * @param node
     * @param newColour
     * @param time
     */
    public void addChange(Node node, int newColour, double time) {
        int count = cTree.getChangeCount(node);

        if (count>=cTree.getMaxBranchColours())
            throw new RuntimeException(
                    "Maximum number of colour changes along branch exceeded.");

        // Add spot for new colour change:
        setChangeCount(node, count+1);

        // Set change colour and time:
        setChangeColour(node, count, newColour);
        setChangeTime(node, count, time);

    }

	/**
	 * Set colour of node.
	 * 
	 * @param node
	 * @param colour New colour for node.
	 */
	public void setNodeColour(Node node, int colour) {
//        cTree.nodeColoursInput.get().setValue(node.getNr(), colour);
        nodeColoursInput.get().setValue(node.getNr(), colour);
	}

}
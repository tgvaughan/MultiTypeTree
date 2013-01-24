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
package beast.evolution.operators;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Operator;
import beast.evolution.tree.ColouredTree;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class for all operators on ColouredTree objects.
 *
 * @author Tim Vaughan
 */
@Description("This operator generates proposals for a coloured beast tree.")
public abstract class ColouredTreeOperator extends Operator {

    public Input<ColouredTree> colouredTreeInput = new Input<ColouredTree>(
            "colouredTree", "Coloured tree on which to operate.",
            Validate.REQUIRED);
    protected Tree tree;
    protected ColouredTree cTree;
    
    /**
     * Exception thrown when maximum colour changes is exceeded.
     */
    protected class RecolouringException extends Exception {
        ColouredTree cTree;
        public RecolouringException(ColouredTree cTree) {
            this.cTree = cTree;
        };
        public void discardMsg() {
            System.err.println("WARNING: Discarding proposal due to maximum "
                    + "number of colours allowed on branch being exceeded. ("
                    + cTree.getNDiscardsAndIncrement() + " such discards so-far.)");
        }
        public void throwRuntime() {
            throw new RuntimeException("Maximum number of colour changes "
                    + "allowed on branch exceeded.");
        }
    };
    
    /* ***********************************************************************
     * The following two methods are copied verbatim from TreeOperator.  We've
     * done this as extending TreeOperator would mean forcing every subclass
     * of ColouredTreeOperator to require both a ColouredTree and a Tree as
     * inputs.
     */
    
    /**
     * Obtain the sister of node "child" having parent "parent".
     * 
     * @param parent the parent
     * @param child  the child that you want the sister of
     * @return the other child of the given parent.
     */
    protected Node getOtherChild(Node parent, Node child) {
        if (parent.getLeft().getNr() == child.getNr()) {
            return parent.getRight();
        } else {
            return parent.getLeft();
        }
    }

    /**
     * Replace node "child" with another node.
     *
     * @param node
     * @param child
     * @param replacement
     */
    public void replace(Node node, Node child, Node replacement) {
        replacement.setParent(node);
        if (node.getLeft().getNr() == child.getNr()) {
            node.setLeft(replacement);
        } else {
            // it must be the right child
            node.setRight(replacement);
        }
        node.makeDirty(Tree.IS_FILTHY);

        replacement.makeDirty(Tree.IS_FILTHY);
    }
    
    /* **********************************************************************/

    
    /**
     * Swap the colouring information associated with nodeA and nodeB.
     *
     * @param nodeA
     * @param nodeB
     */
    public void swapNodes(Node nodeA, Node nodeB) {

        // Store old contents of nodeB:

        int oldChangeCountB = cTree.getChangeCount(nodeB);
        int oldNodeColourB = cTree.getNodeColour(nodeB);
        List<Integer> changeList = new ArrayList<Integer>();
        List<Double> changeTimeList = new ArrayList<Double>();

        for (int i = 0; i < oldChangeCountB; i++) {
            changeList.add(cTree.getChangeColour(nodeB, i));
            changeTimeList.add(cTree.getChangeTime(nodeB, i));
        }

        // Copy contents of nodeA to nodeB:

        setChangeCount(nodeB, 0);
        for (int i = 0; i < cTree.getChangeCount(nodeA); i++)
            try {
                addChange(nodeB, cTree.getChangeColour(nodeA, i),
                        cTree.getChangeTime(nodeA, i));
            } catch (Exception ex) {
                Logger.getLogger(ColouredTreeOperator.class.getName()).log(Level.SEVERE, null, ex);
            }
        setNodeColour(nodeB, cTree.getNodeColour(nodeA));

        // Copy original contents of nodeB to nodeA:

        setChangeCount(nodeA, 0);
        for (int i = 0; i < oldChangeCountB; i++)
            try {
                addChange(nodeA, changeList.get(i), changeTimeList.get(i));
            } catch (Exception ex) {
                Logger.getLogger(ColouredTreeOperator.class.getName()).log(Level.SEVERE, null, ex);
            }
        setNodeColour(nodeA, oldNodeColourB);

    }

    /**
     * Define a new root for the coloured tree.
     *
     * @param node Node to become root.
     */
    public void setRoot(Node node) {
        if (node != tree.getRoot()) {
            swapNodes(node, tree.getRoot());
            tree.setRoot(node);
        }
    }

    /**
     * Disconnect edge <node,node.getParent()> by joining node's sister directly
     * to node's grandmother and adding all colour changes previously on
     * <node.getParent(),node.getParent().getParent()> to the new branch.
     *
     * @param node
     */
    public void disconnectBranch(Node node) throws RecolouringException {

        // Check argument validity:
        Node parent = node.getParent();
        if (node.isRoot() || parent.isRoot())
            throw new IllegalArgumentException("Illegal argument to "
                    + "disconnectBranch().");

        Node sister = getOtherChild(parent, node);

        // Add colour changes originally attached to parent to those attached
        // to node's sister:
        for (int idx = 0; idx < cTree.getChangeCount(parent); idx++) {
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
     * Disconnect node from root, discarding all colouring on <node,root> and
     * <node's sister,root>.
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
     * Creates a new branch between node and a new node at time destTime between
     * destBranchBase and its parent. Colour changes are divided between the two
     * new branches created by the split.
     *
     * @param node
     * @param destBranchBase
     * @param destTime
     */
    public void connectBranch(Node node, Node destBranchBase, double destTime) {

        // Check argument validity:
        if (node.isRoot() || destBranchBase.isRoot())
            throw new IllegalArgumentException("Illegal argument to "
                    + "connectBranch().");

        // Obtain existing parent of node and set new time:
        Node parent = node.getParent();
        parent.setHeight(destTime);

        // Determine where the split comes in the list of colour changes
        // attached to destBranchBase:
        int split;
        for (split = 0; split < cTree.getChangeCount(destBranchBase); split++)
            if (cTree.getChangeTime(destBranchBase, split) > destTime)
                break;

        // Divide colour changes between new branches:
        setChangeCount(parent, 0);
        for (int idx = split; idx < cTree.getChangeCount(destBranchBase); idx++)
            try {
                addChange(parent, cTree.getChangeColour(destBranchBase, idx),
                        cTree.getChangeTime(destBranchBase, idx));
            } catch (Exception ex) {
                Logger.getLogger(ColouredTreeOperator.class.getName()).log(Level.SEVERE, null, ex);
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
     * Set up node's parent as the new root with a height of destTime, with
     * oldRoot as node's new sister.
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

        if (newRoot.getLeft() == node)
            newRoot.setRight(oldRoot);
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

        if (idx > cTree.getChangeCount(node))
            throw new IllegalArgumentException(
                    "Attempted to alter non-existent change colour.");

        int offset = node.getNr() * cTree.getMaxBranchColours();
        cTree.changeColoursInput.get().setValue(offset + idx, colour);

    }

    /**
     * Sets colour changes along branch between node and its parent.
     *
     * @param node
     * @param colours Vararg list of colour indices.
     */
    public void setChangeColours(Node node, int... colours) {

        if (colours.length > cTree.getMaxBranchColours())
            throw new IllegalArgumentException(
                    "Maximum number of colour changes along branch exceeded.");

        int offset = node.getNr() * cTree.getMaxBranchColours();
        for (int i = 0; i < colours.length; i++)
            cTree.changeColoursInput.get().setValue(offset + i, colours[i]);

    }

    /**
     * Set new time for change which has already been recorded.
     *
     * @param node
     * @param idx
     * @param time
     */
    public void setChangeTime(Node node, int idx, double time) {

        if (idx > cTree.getChangeCount(node))
            throw new IllegalArgumentException(
                    "Attempted to alter non-existent change time.");

        int offset = node.getNr() * cTree.getMaxBranchColours();
        cTree.changeTimesInput.get().setValue(offset + idx, time);
    }

    /**
     * Sets times of colour changes along branch between node and its parent.
     *
     * @param node
     * @param times Vararg list of colour change times.
     */
    public void setChangeTimes(Node node, double... times) {

        if (times.length > cTree.getMaxBranchColours())
            throw new IllegalArgumentException(
                    "Maximum number of colour changes along branch exceeded.");

        int offset = cTree.getBranchOffset(node);
        for (int i = 0; i < times.length; i++)
            cTree.changeTimesInput.get().setValue(offset + i, times[i]);
    }

    /**
     * Set number of colours on branch between node and its parent.
     *
     * @param node
     * @param count Number of colours on branch. (>=1)
     */
    public void setChangeCount(Node node, int count) {
        cTree.changeCountsInput.get().setValue(node.getNr(), count);
    }

    /**
     * Add a colour change to a branch between node and its parent.
     *
     * @param node
     * @param newColour
     * @param time
     */
    public void addChange(Node node, int newColour, double time) throws RecolouringException {
        int count = cTree.getChangeCount(node);

        if (count >= cTree.getMaxBranchColours())
            throw new RecolouringException(cTree);

        // Add spot for new colour change:
        setChangeCount(node, count + 1);

        // Set change colour and time:
        setChangeColour(node, count, newColour);
        setChangeTime(node, count, time);

    }
    
    /**
     * Insert a new colour change at index idx to colour newColour at the
     * specified time.
     * 
     * @param node
     * @param idx
     * @param newColour
     * @param newTime
     * @throws beast.evolution.operators.ColouredTreeOperator.RecolouringException 
     */
    public void insertChange(Node node, int idx, int newColour, double newTime) throws RecolouringException {
        int count = cTree.getChangeCount(node);
        
        if (idx > count)
            throw new IllegalArgumentException("Index to insertChange() out of range.");

        if (count >= cTree.getMaxBranchColours())
            throw new RecolouringException(cTree);
        
        setChangeCount(node, count+1);

        for (int i=count; i>idx; i--) {
            setChangeTime(node, i, cTree.getChangeTime(node, i-1));
            setChangeColour(node, i, cTree.getChangeColour(node, i-1));
        }
        
        setChangeTime(node, idx, newTime);
        setChangeColour(node, idx, newColour);
    }
    
    /**
     * Remove the colour change at index idx from the branch above node.
     * 
     * @param node
     * @param idx 
     */
    public void removeChange(Node node, int idx) {
        int count = cTree.getChangeCount(node);
        
        if (idx >= count)
            throw new IllegalArgumentException("Index to removeChange() out of range.");
        
        for (int i=idx; i<count-1; i++) {
            setChangeTime(node, i, cTree.getChangeTime(node, i+1));
            setChangeColour(node, i, cTree.getChangeColour(node, i+1));
        }
        
        setChangeCount(node, count-1);
        
    }

    /**
     * Set colour of node.
     *
     * @param node
     * @param colour New colour for node.
     */
    public void setNodeColour(Node node, int colour) {
        cTree.nodeColoursInput.get().setValue(node.getNr(), colour);
    }

    /**
     * Set height (age) of node.
     * 
     * @param node
     * @param height 
     */
    public void setNodeHeight(Node node, double height) {
        node.setHeight(height);
    }
    
    /**
     * Set nodeB to be the left child of nodeA.
     * 
     * @param parentNode
     * @param childNode 
     */
    public void setNodeChildLeft(Node parentNode, Node childNode) {
        parentNode.setLeft(childNode);
    }
    
    /**
     * Set childNode to be the right child of parentNode.
     * 
     * @param parentNode
     * @param childNode 
     */
    public void setNodeChildRight(Node parentNode, Node childNode) {
        parentNode.setRight(childNode);
    }
    
    /**
     * Set parentNode to be the parent of childNode.
     * 
     * @param childNode
     * @param parentNode 
     */
    public void setNodeParent(Node childNode, Node parentNode) {
        childNode.setParent(parentNode);
    }

}
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
import beast.util.Randomizer;
import beast.util.TreeParser;

import java.util.Arrays;

/**
 * BEAST 2 plugin for specifying migration events along a tree.
 *
 * @author Tim Vaughan
 */
@Description("Plugin for specifying migration events along a tree.")
public class ColouredTree extends CalculationNode {

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

	/*
	 * Cache for final branch colours:
	 */

    protected Integer[] finalColours;
    protected Boolean[] finalColoursDirty;

    public ColouredTree() {};

	/**
	 * Constructor to get coloured tree from coloured (single child)
	 * newick tree.
	 * 
	 * @param newick
	 * @param colourLabel
	 * @param nColours
	 * @param maxBranchColours
	 * @throws Exception 
	 */
    public ColouredTree(String newick, String colourLabel, int nColours, int maxBranchColours) throws Exception {

		TreeParser treeParser = new TreeParser("", false);
        treeParser.setInputValue("singlechild", true);
        treeParser.setInputValue("newick", newick);
        treeParser.initAndValidate();

        this.colourLabel = colourLabel;
        this.nColours = nColours;
        this.maxBranchColours = maxBranchColours;

        leafColours = new IntegerParameter(new Integer[treeParser.getLeafNodeCount()]);

        int nBranches = treeParser.getNodeCount()-1;

        Integer[] cols = new Integer[nBranches*maxBranchColours]; Arrays.fill(cols,-1);
        Double[] times = new Double[nBranches*maxBranchColours];  Arrays.fill(times,-1.);
        Integer[]  counts =  new Integer[nBranches]; Arrays.fill(counts,0);

        setupColourParameters(treeParser.getRoot(),cols, times, counts);

        changeColours = new IntegerParameter(cols);
        changeTimes = new RealParameter(times);
        changeCounts = new IntegerParameter(counts);

		// Allocate and initialise array for lazily recording
		// final colour of each branch:
		finalColours = new Integer[nBranches];
		finalColoursDirty = new Boolean[nBranches];
		for (int i=0; i<nBranches; i++) {
			finalColours[i] = 0;
			finalColoursDirty[i] = true;
		}

		// Assign parsed tree to official tree topology field:
		tree = treeParser;

		// Ensure colouring is internally consistent:
		if (!isValid())
			throw new Exception("Inconsistent colour assignment.");

		// Assign tree to input plugin:
		treeInput.setValue(tree, this);
    }

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

        // Allocate and initialise arrays for lazily recording
		// final colour of each branch:
        finalColours = new Integer[tree.getNodeCount()];
        finalColoursDirty = new Boolean[tree.getNodeCount()];
		for (int i=0; i<nBranches; i++) {
			finalColours[i] = 0;
			finalColoursDirty[i] = true;
		}

        // Need to recalculate all final branch colours:
        for (int i=0; i<tree.getNodeCount(); i++)
            finalColoursDirty[i] = true;

    }


	/**
	 * Set up colours for newick constructor .
	 * 
	 * @param node
	 * @param cols
	 * @param times
	 * @param counts 
	 */
    private void setupColourParameters(Node node, Integer[] cols, Double[] times, Integer[] counts){

        int nodeState = Integer.parseInt(node.m_sMetaData.split("=")[1].replaceAll("'","").replaceAll("\"",""));
        int nodeNr = node.getNr();

        if (node.getChildCount() == 1){

            Node child1 = node.getLeft();
            int child1state = Integer.parseInt(child1.m_sMetaData.split("=")[1].replaceAll("'","").replaceAll("\"",""));

            if (child1state!=nodeState)
                addChange(child1.getNr(), maxBranchColours*child1.getNr()+counts[nodeNr], nodeState, node.getHeight(), cols, times, counts);
            setupColourParameters(child1,cols, times, counts);
        }

        if (node.getChildCount()==2){

            Node child1 = node.getLeft();
            int child1state = Integer.parseInt(child1.m_sMetaData.split("=")[1].replaceAll("'","").replaceAll("\"",""));

            Node child2 = node.getRight();
            int child2state = Integer.parseInt(child2.m_sMetaData.split("=")[1].replaceAll("'","").replaceAll("\"",""));

            if (child1state!=nodeState)
                addChange(child1.getNr(), maxBranchColours*child1.getNr()+counts[nodeNr], nodeState, node.getHeight(), cols, times, counts);
            if (child2state!=nodeState)
                addChange(child2.getNr(), maxBranchColours*child2.getNr()+counts[nodeNr], nodeState, node.getHeight(), cols, times, counts);

            setupColourParameters(child1,cols, times, counts);
            setupColourParameters(child2,cols, times, counts);

        }

    }

	/**
	 * Set up colours for newick constructor.
	 * 
	 * @param nodeNr
	 * @param index
	 * @param colour
	 * @param time
	 * @param cols
	 * @param times
	 * @param counts 
	 */
    private void addChange(int nodeNr, int index, int colour, double time, Integer[] cols, Double[] times, Integer[] counts){

        cols[index] = colour;
        times[index] = time;
        counts[nodeNr]++;

    }


    /**
     * Retrieve uncoloured component of this coloured tree.
     *
     * @return Tree object.
     */
    public Tree getUncolouredTree() {
        return tree;
    }

	/**
	 * Check whether there is space for a branch above the specified
	 * node on the coloured tree.
	 * 
	 * This is subtly different to calling node.isRoot(), which will return
	 * true for all frontier nodes during the bottom-up construction of
	 * a tree topology.
	 * 
	 * @param node
	 * @return True if space for the branch exists.
	 */
	public boolean hasBranch(Node node) {
		if (node.getNr()>=changeCounts.getDimension())
			return false;
		else
			return true;
	}


    /**
     * Retrieve colour label.
     *
     * @return String object.
     */
    public String getColourLabel(){
        return colourLabel;
    }

    /**
     * Retrieve the total number of colour changes in tree.
     *
     * @return int object.
     */
    public int getTotalNumberofChanges(){
        int changes = 0;
        for (int i=0;i<changeCounts.getDimension(); i++){
            changes += changeCounts.getValue(i);
        }
        return changes;
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

        // Mark cached final branch colour as dirty if necessary:
        if (idx==getChangeCount(node)-1)
            markFinalBranchColourDirty(node);
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

        // Mark cached final branch colour as dirty:
        markFinalBranchColourDirty(node);

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

        // Mark cached final branch colour as dirty:
        markFinalBranchColourDirty(node);
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
            throw new IllegalArgumentException(
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
            throw new IllegalArgumentException(
                    "Index to getChangeTime exceeded total number of colour"
                            + "changes on branch.");

        return changeTimes.getValue(getBranchOffset(node)+idx);
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
     * Retrieve final colour on branch between node and its parent.
     *
     * @param node
     * @return Final colour.
     */
    public int getFinalBranchColour(Node node) {
        if (!hasBranch(node))
            throw new IllegalArgumentException("Argument to getFinalBranchColour"
                    + "is not the bottom of a branch.");

        if (finalColoursDirty[node.getNr()]) {
            if (getChangeCount(node)>0)
                finalColours[node.getNr()] = getChangeColour(node,
                        getChangeCount(node)-1);
            else
                finalColours[node.getNr()] = getInitialBranchColour(node);

            finalColoursDirty[node.getNr()] = false;
        }

        return finalColours[node.getNr()];
    }

    /**
     * Retrieve initial colour on branch between node and its parent.
     *
     * @param node
     * @return Initial colour.
     */
    public int getInitialBranchColour(Node node) {
        if (node.isLeaf())
            return getLeafColour(node);
        else
            return getFinalBranchColour(node.getLeft());
    }

    /**
     * Mark cached final branch colour along
     *
     * @param node
     */
    private void markFinalBranchColourDirty(Node node) {

        if (!hasBranch(node))
            throw new IllegalArgumentException("Argument to"
                    + "markFinalBranchColourDirty is not the bottom"
                    + "of a branch.");

        finalColoursDirty[node.getNr()] = true;

        if (node.getParent() != null && getChangeCount(node.getParent())==0)
            markFinalBranchColourDirty(node.getParent());
    }

    /**
     * Retrieve time of final colour change on branch, or height of
     * bottom of branch if branch has no colour changes.
     *
     * @param node
     * @return Time of final colour change.
     */
    public double getFinalBranchTime(Node node) {

        if (node.isRoot())
            throw new IllegalArgumentException("Argument to"
                    + "getFinalBranchTime is not the bottom of a branch.");

        if (getChangeCount(node)>0)
            return getChangeTime(node, getChangeCount(node)-1);
        else
            return node.getHeight();
    }

    /**
     * Checks validity of current colour assignment.
     *
     * @return True if valid, false otherwise.
     */
    public boolean isValid() {
        return colourIsValid(tree.getRoot()) && timeIsValid(tree.getRoot());
    }

    /**
     * Recursively check validity of colours assigned to branches of subtree
     * under node.
     *
     * @param node Root node of subtree to validate.
     * @return True if valid, false otherwise.
     */
    private boolean colourIsValid(Node node) {

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
            if (!child.isLeaf() && !colourIsValid(child))
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
    private boolean timeIsValid(Node node) {

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
                if (!timeIsValid(child))
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
     * using the a ColouredTree instance with methods designed to act on
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

            Node startNode = flatTree.getNode(nodeNum);
            startNode.setMetaData(colourLabel,
                    getInitialBranchColour(node));

            Node endNode = startNode.getParent();

            Node branchNode = startNode;
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

            // Ensure final branchNode is connected to the original parent:
            branchNode.setParent(endNode);
            if (endNode.getLeft() == startNode)
                endNode.setLeft(branchNode);
            else
                endNode.setRight(branchNode);
        }

        return flatTree;
    }

    /**
     * Determine whether colour exists somewhere on the portion of the branch
     * between node and its parent with age greater than t.
     *
     * @param node
     * @param t
     * @param colour
     * @return True if colour is on branch.
     */
    public boolean colourIsOnSubBranch(Node node, double t, int colour) {

        if (node.isRoot())
            throw new IllegalArgumentException("Node argument to"
                    + "colourIsOnSubBranch is not the bottom of a branch.");

        if (t>node.getParent().getHeight())
            throw new IllegalArgumentException("Time argument to"
                    + "colourIsOnSubBranch is not on specified branch.");

        int thisColour = getInitialBranchColour(node);

        for (int i=0; i<getChangeCount(node); i++) {
            if (getChangeTime(node,i)>=t && thisColour==colour)
                return true;

            thisColour = getChangeColour(node,i);
        }

        if (thisColour==colour)
            return true;

        return false;
    }

    public double getColouredSegmentLength(Node node, double t, int colour) {

        if (node.isRoot())
            throw new IllegalArgumentException("Node argument to"
                    + "getColouredSegmentLength is not the bottom of a branch.");

        if (t>node.getParent().getHeight())
            throw new IllegalArgumentException("Time argument ot"
                    + "getColouredSegmentLength is not on specified branch.");

        // Determine total length of time that sub-branch has chosen colour:
        int lastColour = getInitialBranchColour(node);
        double lastTime = node.getHeight();

        double norm=0.0;
        for (int i=0; i<getChangeCount(node); i++) {
            int thisColour = getChangeColour(node,i);
            double thisTime = getChangeTime(node,i);

            if (lastColour==colour && thisTime>t)
                norm += thisTime- Math.max(t,lastTime);

            lastColour = thisColour;
            lastTime = thisTime;
        }

        if (lastColour==colour)
            norm += node.getParent().getHeight()-Math.max(t,lastTime);

        // Return negative result if colour is not on sub-branch:
        if (!(norm>0.0))
            return -1.0;

        return norm;
    }

    /**
     * Select a time from a uniform distribution over the times within
     * that portion of the branch which has an age greater than t as well
     * as the specified colour. Requires pre-calculation of total length
     * of sub-branch following t having specified colour.
     *
     * @param node
     * @param t
     * @param colour
     * @param norm Total length of sub-branch having specified colour.
     * @return Randomly selected time or -1 if colour does not exist on
     * branch at age greater than t.
     */
    public double chooseTimeWithColour(Node node, double t, int colour,
                                       double norm) {

        if (node.isRoot())
            throw new IllegalArgumentException("Node argument to"
                    + "chooseTimeWithColour is not the bottom of a branch.");

        if (t>node.getParent().getHeight() || t<node.getHeight())
            throw new IllegalArgumentException("Time argument to"
                    + "chooseTimeWithColour is not on specified branch.");

        // Select random time within appropriately coloured region:
        double alpha = norm*Randomizer.nextDouble();

        // Convert to absolute time:
        int lastColour = getInitialBranchColour(node);
        double lastTime = node.getHeight();

        double tChoice = t;
        for (int i=0; i<getChangeCount(node); i++) {
            int thisColour = getChangeColour(node,i);
            double thisTime = getChangeTime(node,i);

            if (lastColour==colour && thisTime>t)
                alpha -= thisTime-Math.max(t,lastTime);

            if (alpha<0) {
                tChoice = thisTime + alpha;
                break;
            }

            lastColour = thisColour;
            lastTime = thisTime;
        }

        if (alpha>0)
            tChoice = alpha+lastTime;

        return tChoice;
    }

}

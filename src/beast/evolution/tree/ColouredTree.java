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
import java.util.List;
import java.util.ArrayList;
import java.io.PrintStream;


/**
 * BEAST 2 plugin for specifying migration events along a tree.
 *
 * @author Tim Vaughan
 */
@Description("Plugin for specifying migration events along a tree.")
public class ColouredTree extends CalculationNode implements Loggable{

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

    public Input<IntegerParameter> changeColoursInput = new Input<IntegerParameter>(
            "changeColours", "Changes in colour along branches");

    public Input<RealParameter> changeTimesInput = new Input<RealParameter>(
            "changeTimes", "Times of colour changes.");

    public Input<IntegerParameter> changeCountsInput = new Input<IntegerParameter>(
            "changeCounts", "Number of colour changes on each branch.");

	public Input<IntegerParameter> nodeColoursInput = new Input<IntegerParameter>(
			"nodeColours", "Colour at each node (including internal nodes).");

    /*
     * Shadowing fields:
     */

    protected String colourLabel;
    protected Integer nColours, maxBranchColours;
    protected IntegerParameter leafColours;

    protected Tree tree;
    protected Tree flatTree; // the flat tree with single child nodes and colours as metadata, for logging
	public IntegerParameter nodeColours, changeColours, changeCounts;
    protected RealParameter changeTimes;

    public ColouredTree() {};

    /**
     * Constructor to get coloured tree from coloured (single child)
     * newick tree.
     *
     * @param colourLabel
     * @param nColours
     * @param maxBranchColours
     * @throws Exception
     */
    public ColouredTree(Tree tree, TreeParser treeParser, String colourLabel, int nColours, int maxBranchColours) throws Exception {

//        TreeParser treeParser = new TreeParser("", false);
//        treeParser.initByName("adjustTipHeights",false, "singlechild", true, "newick", newick);

        this.colourLabel = colourLabel;
        this.nColours = nColours;
        this.maxBranchColours = maxBranchColours;

        List<Integer> markedForDeletion = new ArrayList<Integer>();
        int nNodes = treeParser.getNodeCount();

        Integer[] cols = new Integer[nNodes*maxBranchColours]; Arrays.fill(cols,-1);
        Double[] times = new Double[nNodes*maxBranchColours];  Arrays.fill(times,-1.);
        Integer[]  counts =  new Integer[nNodes]; Arrays.fill(counts,0);

        setupColourParameters(treeParser.getRoot(),cols, times, counts);

        // Assign parsed tree to official tree topology field and remove single children from it:
//        tree = treeParser;
        nNodes -= getSingleChildCount(treeParser, markedForDeletion) ;
        treeParser = removeSingleChildren(treeParser, markedForDeletion);

        assignColourArrays(treeParser, cols, times, counts, nNodes);

        TreeParser binaryTree = treeParser;
        binaryTree.setInputValue("adjustTipHeights",false);
        binaryTree.setInputValue("singlechild", false);

        // Assign tree to input plugin:
        this.tree = tree.copy();
        this.tree.assignFromWithoutID(binaryTree);

        // Ensure colouring is internally consistent:
        if (!isValid())
            throw new Exception("Inconsistent colour assignment.");

     }

    @Override
    public void initAndValidate() throws Exception {

        // Grab primary colouring parameters from inputs:
        colourLabel = colourLabelInput.get();
        nColours = nColoursInput.get();
        maxBranchColours = maxBranchColoursInput.get();

        // Obtain tree to colour:
        tree = treeInput.get();

        // Obtain references to Parameters used to store colouring:
        changeColours = changeColoursInput.get();
        changeTimes = changeTimesInput.get();
        changeCounts = changeCountsInput.get();
		nodeColours = nodeColoursInput.get();

        // Allocate arrays for recording colour change information:
        initParameters(tree.getNodeCount());

    }

	/**
	 * Allocate memory for recording tree colour information.
	 * 
	 * @param nNodes Number of nodes tree will contain.
	 */
	public void initParameters(int nNodes) {

        changeColours.setDimension(nNodes*maxBranchColours);
        changeTimes.setDimension(nNodes*maxBranchColours);
        changeCounts.setDimension(nNodes);
		nodeColours.setDimension(nNodes);


//		for (int n=0; n<nNodes; n++) {
//			changeCounts.setValue(n, 0);
//			nodeColours.setValue(n, 0);
//		}
	}


    public int getMaxBranchColours(){
        return maxBranchColours;
    }


    /**
     * Get number of single child nodes of a tree.
     *
     * @param tree The tree.
     */

    public int getSingleChildCount(Tree tree, List<Integer> markedForDeletion){

        int count = 0;

        return getSingleChildCount(tree.getRoot(), markedForDeletion);

    }

    /**
     * Get number of single child nodes of a tree.
     *
     * @param node The root of the (sub)tree.
     */
    public int getSingleChildCount(Node node, List<Integer> markedForDeletion){

        if (node.isLeaf()) return 0;

        if (node.getChildCount()==1) {

            markedForDeletion.add(node.getNr());
            return 1 + getSingleChildCount(node.getLeft(), markedForDeletion);
        }

        else return getSingleChildCount(node.getLeft(), markedForDeletion) + getSingleChildCount(node.getRight(), markedForDeletion);

    }

    /**
     * Delete single child nodes from tree
     *
     * @param markedForDeletion list of single node numbers 
     */
	public TreeParser removeSingleChildren(TreeParser treeParser, List<Integer> markedForDeletion) throws Exception {

        List<Node> nodes= new ArrayList<Node>();

        for (int i : markedForDeletion){
            nodes.add(treeParser.getNode(i));
        }

        for (Node node : nodes) {

            if (node.getChildCount()!=1)
				throw new RuntimeException("Error: node marked for deletion"
						+ " should have exactly one child node, but has "
						+ node.getChildCount());

            Node parent = node.getParent();

            if (node==parent.getLeft()) {
                parent.setLeft(node.getLeft());
                parent.getLeft().setParent(parent);

            }
            else if (node==parent.getRight()){
                parent.setRight(node.getLeft());
                parent.getRight().setParent(parent);

            }
        }

        treeParser.nodeCount = treeParser.getNodeCount() - nodes.size();
        treeParser.internalNodeCount = treeParser.getInternalNodeCount() - nodes.size();

        treeParser.getRoot().labelInternalNodes(treeParser.getLeafNodeCount());
        treeParser.setInputValue("newick", null);
        treeParser.initArrays();

        return treeParser;

    }

    /**
     * Set up colours for Newick constructor using recursive decent.
     *
     * @param node
     * @param cols
     * @param times
     * @param counts
     */
    private void setupColourParameters(Node node, Integer[] cols,
			Double[] times, Integer[] counts) {

        int nodeState = Integer.parseInt(node.m_sMetaData.split("=")[1].replaceAll("'","").replaceAll("\"",""));
        int nodeNr = node.getNr();
        node.setMetaData("oldNodeNumber", nodeNr);

        if (node.getChildCount() == 1){

            Node child1 = node.getLeft();
            int child1state = Integer.parseInt(child1.m_sMetaData.split("=")[1].replaceAll("'","").replaceAll("\"",""));

            if (child1.getChildCount()==1){

                int[] changes = new int[maxBranchColours];
                double[] changetimes = new double[maxBranchColours];
                changes[0] = nodeState;
                changetimes[0] = node.getHeight();

                int i = 1;
                node = child1;

                while (node.getChildCount()==1 && i < maxBranchColours){

                    changes[i] = Integer.parseInt(node.m_sMetaData.split("=")[1].replaceAll("'","").replaceAll("\"",""));
                    changetimes[i] = node.getHeight();
                    i++;
                    node = node.getLeft();
                }

                if (node.getChildCount()==1 && i == maxBranchColours) throw new RuntimeException("Too many changes on branch!");

                nodeNr = node.getNr();

                for (int j=i-1; j>=0; j--){
                    addChange(nodeNr, maxBranchColours*nodeNr+counts[nodeNr], changes[j], changetimes[j], cols, times, counts);
                }

                setupColourParameters(node ,cols, times, counts);


            } else {

                nodeNr = child1.getNr();
                if (child1state!=nodeState)
                    addChange(nodeNr, maxBranchColours*nodeNr+counts[nodeNr], nodeState, node.getHeight(), cols, times, counts);

                setupColourParameters(child1 ,cols, times, counts);
            }
        }

        else if (node.getChildCount()==2){

            Node child1 = node.getLeft();
            int child1state = Integer.parseInt(child1.m_sMetaData.split("=")[1].replaceAll("'","").replaceAll("\"",""));

            Node child2 = node.getRight();
            int child2state = Integer.parseInt(child2.m_sMetaData.split("=")[1].replaceAll("'","").replaceAll("\"",""));

            if (child1state!=nodeState){
                nodeNr = child1.getNr();
                addChange(nodeNr, maxBranchColours*nodeNr+counts[nodeNr], nodeState, node.getHeight(), cols, times, counts);
            }
            if ( child2state!=nodeState) {
                nodeNr = child2.getNr();
                addChange(nodeNr, maxBranchColours*nodeNr+counts[nodeNr], nodeState, node.getHeight(), cols, times, counts);
            }

            setupColourParameters(child1 ,cols, times, counts);
            setupColourParameters(child2 ,cols, times, counts);

        }

//        else if (node.isLeaf()){
//            leafCols[nodeNr] = nodeState;
//        }

    }

    private void assignColourArrays(TreeParser treeParser, Integer[] cols, Double[] times, Integer[] counts, int nNodes) throws Exception{


        Integer[] cols_afterRemoval = new Integer[nNodes*maxBranchColours]; Arrays.fill(cols_afterRemoval,-1);
        Double[] times_afterRemoval = new Double[nNodes*maxBranchColours];  Arrays.fill(times_afterRemoval,-1.);
        Integer[] counts_afterRemoval =  new Integer[nNodes]; Arrays.fill(counts_afterRemoval,0);
        Integer[] nodeCols = new Integer[nNodes];

        Node node;
        int oldNodeNumber;

        for (int i =0; i<nNodes; i++){
            node = treeParser.getNode(i);

			nodeCols[i] = Integer.parseInt(node.m_sMetaData
					.split("=")[1]
					.replaceAll("'","")
					.replaceAll("\"",""));

            oldNodeNumber = (Integer) node.getMetaData("oldNodeNumber");
            node.m_sMetaData= null;
            counts_afterRemoval[i] = counts[oldNodeNumber];

            for (int j=0; j<counts_afterRemoval[i]; j++){

                cols_afterRemoval[maxBranchColours*i+j] = cols[maxBranchColours*oldNodeNumber+j];
                times_afterRemoval[maxBranchColours*i+j] = times[maxBranchColours*oldNodeNumber+j];
            }
        }


        // initialize statenodes:

        changeColours = new IntegerParameter(cols);
        changeTimes = new RealParameter(times);
        changeCounts = new IntegerParameter(counts);
        nodeColours = new IntegerParameter(nodeCols);

        IntegerParameter cols_I= new IntegerParameter(cols_afterRemoval);
        RealParameter times_I   = new RealParameter(times_afterRemoval);
        IntegerParameter counts_I = new IntegerParameter(counts_afterRemoval);
        IntegerParameter nodeCols_I = new IntegerParameter(nodeCols);

        changeColours.assignFromWithoutID(cols_I);
        changeTimes.assignFromWithoutID( times_I);
        changeCounts.assignFromWithoutID(counts_I);
        nodeColours.assignFromWithoutID(nodeCols_I);

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
     * Retrieve colour label.
     *
     * @return String object.
     */
    public String getColourLabel(){
        return colourLabel;
    }

	/**
	 * Retrieve number of distinct colours in model.
	 * 
	 * @return Number of colours.
	 */
	public int getNColours() {
		return nColours;
	}

    /**
     * Retrieve the total number of colour changes in tree.
     *
     * @return Total change count.
     */
    public int getTotalNumberofChanges(){
        int changes = 0;
        for (int i=0;i<changeCounts.getDimension(); i++){
            changes += changeCounts.getValue(i);
        }
        return changes;
    }

	/**
	 * Set colour of node.
	 * 
	 * @param node
	 * @param colour New colour for node.
	 */
	public void setNodeColour(Node node, int colour) {
		nodeColours.setValue(node.getNr(), colour);
	}

    /**
     * Obtain colour associated with node of tree.
     *
     * @param node
     * @return Colour index.
     */
    public int getNodeColour(Node node) {
        return nodeColours.getValue(node.getNr());
    }

    public Integer[] getNodeColours() {
         return nodeColours.getValues();
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

		if (getChangeCount(node)>0)
			return getChangeColour(node, getChangeCount(node)-1);
		else
			return getNodeColour(node);
    }

    /**
     * Retrieve time of final colour change on branch, or height of
     * bottom of branch if branch has no colour changes.
     *
     * @param node
     * @return Time of final colour change.
     */
    public double getFinalBranchTime(Node node) {

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

        int nodeColour = getNodeColour(node);
        for (Node child : node.getChildren()) {

            // Check that final child branch colour parent node colour:
			if (getFinalBranchColour(child) != nodeColour)
				return false;

            // Check that subtree of child is also valid:
            if (!colourIsValid(child))
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


    // check if the subtree with root node has single children
    public Boolean hasSingleChildrenWithoutColourChange(Node node){


//        System.out.println(node.getMetaData(colourLabel));
        int h = (Integer) node.getMetaData(colourLabel);
        if (node.getChildCount()==1) {
            if (node.getMetaData(colourLabel).equals(node.getChild(0).getMetaData(colourLabel)))
                return true;
        }

        if (node.getChildCount()==2)
            return (hasSingleChildrenWithoutColourChange(node.getChild(0)) || hasSingleChildrenWithoutColourChange(node.getChild(1)));

        return false;
    }


    /**
     * Generates a new tree in which the colours along the branches are
     * indicated by the traits of single-child nodes.
     *
     * This method is useful for interfacing trees coloured externally
     * using the a ColouredTree instance with methods designed to act on
     * trees coloured using single-child nodes and their metadata fields.
	 * 
	 * Caveat: assumes more than one node exists on tree (i.e. leaf != root)
     *
     * @return Flattened tree.
     */
    public Tree getFlattenedTree() {

        // Create new tree to modify.  Note that copy() doesn't
        // initialise the node array lists, so initArrays() must
        // be called manually.
        flatTree = tree.copy();
        flatTree.initArrays();

        int nextNodeNr = tree.getNodeCount();
        Node colourChangeNode;

        for (Node node : tree.getNodesAsArray()) {

            int nodeNum = node.getNr();

            if (node.isRoot()) {
                flatTree.getNode(nodeNum).setMetaData(colourLabel,
                        getFinalBranchColour(node.getLeft()));
                continue;
            }

            Node startNode = flatTree.getNode(nodeNum);
            startNode.setMetaData(colourLabel,
                    getNodeColour(node));
			startNode.m_sMetaData = String.format("&%s=%d",
					colourLabel, getNodeColour(node));


            Node endNode = startNode.getParent();
            endNode.setMetaData(colourLabel,
                    getNodeColour(node.getParent()));
			endNode.m_sMetaData = String.format("&%s=%d",
					colourLabel, getNodeColour(node.getParent()));

            Node branchNode = startNode;
            for (int i=0; i<getChangeCount(node); i++) {

                // Create and label new node:
                colourChangeNode = new Node();
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
				colourChangeNode.m_sMetaData = String.format("&%s=%d",
						colourLabel, getChangeColour(node,i));

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

        int thisColour = getNodeColour(node);

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
        int lastColour = getNodeColour(node);
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
        int lastColour = getNodeColour(node);
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
 
	
	/**
	 * Main for debugging.
	 * 
	 * @param args
	 * @throws Exception 
	 */
    public static void main(String[] args) throws Exception {

        // 4 taxa test tree:
//        String newick = "(((1[&state='1']:1, (2[&state='0']:.5)[&state='1']:1.5)[&state='1']:2)[&state='0']:1, (3[&state='0']:1.5, (4[&state='1']:1.5)[&state='0']:1 )[&state='0']:2)[&state='0']:1;";

        // 100 taxa test tree
        String newick ="(((((\"118_0_136.71\"[&state=\"0\"]:41.32264855260591, \"230_0_122.19\"[&state=\"0\"]:26.793552457020212)[&state=\"0\"]:42.88978384694522, ((\"547_0_130.88\"[&state=\"0\"]:50.54811361295819, (\"566_0_130.68\"[&state=\"0\"]:47.60230491203379, ((((\"865_1_134.45\"[&state=\"1\"]:17.589495694018893)[&state=\"2\"]:0.9540287590253058)[&state=\"0\"]:16.518525077043492, \"892_0_144.36\"[&state=\"0\"]:44.9715478793447)[&state=\"0\"]:10.861776185693998, (\"919_0_138.5\"[&state=\"0\"]:47.92579514598113, ((\"928_1_149.69\"[&state=\"1\"]:51.64686272415632, \"1064_1_144.07\"[&state=\"1\"]:46.02224847050793)[&state=\"1\"]:2.166951742937286)[&state=\"0\"]:5.3036310531208954)[&state=\"0\"]:2.051588531573202)[&state=\"0\"]:5.4447073936282635)[&state=\"0\"]:2.7533911995893448)[&state=\"0\"]:10.498215616261263, ((\"1108_0_94.61\"[&state=\"0\"]:13.64387877305002, ((\"1120_0_118.11\"[&state=\"0\"]:25.17446574407606, (\"1184_0_125.31\"[&state=\"0\"]:23.267530724688484, (\"1221_0_148.83\"[&state=\"0\"]:40.38958384757983, (\"1231_0_137.03\"[&state=\"0\"]:24.135682447347875, \"1233_0_139.04\"[&state=\"0\"]:26.145175303392676)[&state=\"0\"]:4.455323908419004)[&state=\"0\"]:6.401680851206166)[&state=\"0\"]:9.10458072043437)[&state=\"0\"]:0.7140719776866575, \"1369_0_104.75\"[&state=\"0\"]:12.530117397062014)[&state=\"0\"]:11.25447261897618)[&state=\"0\"]:11.101259316558156, \"1378_0_161.43\"[&state=\"0\"]:91.5672892309858)[&state=\"0\"]:0.03631913532922226)[&state=\"0\"]:17.326725010737633)[&state=\"0\"]:7.382899343919462, ((((((\"1458_0_137.88\"[&state=\"0\"]:20.341907354257373)[&state=\"1\"]:13.983611382809713, ((\"1463_0_151.83\"[&state=\"0\"]:24.039904343304514, \"1464_0_151.09\"[&state=\"0\"]:23.303564718196057)[&state=\"0\"]:11.788220711222209)[&state=\"1\"]:12.451096157616533)[&state=\"1\"]:15.658934221263777)[&state=\"0\"]:18.30860361188526, ((\"1489_0_113.52\"[&state=\"0\"]:14.707113794456617, \"1519_0_159.89\"[&state=\"0\"]:61.082231634059255)[&state=\"0\"]:8.644434948888076, (\"1761_0_138.91\"[&state=\"0\"]:47.30674112255649, (\"2008_2_152.6\"[&state=\"2\"]:37.875453603914195)[&state=\"0\"]:23.12369170690762)[&state=\"0\"]:1.4324867469798193)[&state=\"0\"]:20.582607865955794)[&state=\"0\"]:0.10777054396538688, (\"2307_1_120.68\"[&state=\"1\"]:15.813901743369584)[&state=\"0\"]:35.3897189556119)[&state=\"0\"]:20.933988167447026, (((\"2607_0_88.38\"[&state=\"0\"]:19.366374882512474, (\"2617_0_93.48\"[&state=\"0\"]:17.55210806838636, (\"2618_0_97.39\"[&state=\"0\"]:11.704434641336789, \"2673_0_113.78\"[&state=\"0\"]:28.098814334121485)[&state=\"0\"]:9.757469871850873)[&state=\"0\"]:6.911327410004247)[&state=\"0\"]:19.701062156845808, ((((\"2684_0_159.4\"[&state=\"0\"]:76.5953454484171, ((\"2773_0_159.79\"[&state=\"0\"]:47.28506520047266, (\"2832_1_145.88\"[&state=\"1\"]:23.00304098676027)[&state=\"0\"]:10.363576016854353)[&state=\"0\"]:23.75019325494938, (\"2844_2_132.55\"[&state=\"2\"]:30.44038800464382)[&state=\"0\"]:13.35118483987435)[&state=\"0\"]:5.954460703957309)[&state=\"0\"]:0.08628112960789736, ((\"2873_1_154.78\"[&state=\"1\"]:31.729079365706625)[&state=\"0\"]:35.252249422309845, (\"3041_2_151.18\"[&state=\"2\"]:33.23717733601043)[&state=\"0\"]:30.143278775899887)[&state=\"0\"]:5.076013094369273)[&state=\"0\"]:23.832140101904677, ((\"3112_0_122.32\"[&state=\"0\"]:15.05902203493774, (\"3120_1_156.01\"[&state=\"1\"]:23.689378745802117)[&state=\"0\"]:25.054494196795233)[&state=\"0\"]:8.034035685027803, \"3126_0_130.73\"[&state=\"0\"]:31.495678538481457)[&state=\"0\"]:40.343524109579526)[&state=\"0\"]:3.469733500105491, ((\"3175_0_140.62\"[&state=\"0\"]:46.14214150104121, ((\"3219_1_126.73\"[&state=\"1\"]:26.264086581299154, \"3431_1_153.52\"[&state=\"1\"]:53.062023610805454)[&state=\"1\"]:5.593363687881762)[&state=\"0\"]:0.3897949414267572)[&state=\"0\"]:22.83828343135572, (\"3569_0_114.74\"[&state=\"0\"]:17.40955147010338, \"3608_0_158.92\"[&state=\"0\"]:61.586713148860795)[&state=\"0\"]:25.689405427309254)[&state=\"0\"]:16.222933476945123)[&state=\"0\"]:6.105049987056894)[&state=\"0\"]:0.7083520986620613, (((\"3758_0_144.88\"[&state=\"0\"]:22.531839853888087, \"3759_0_142.66\"[&state=\"0\"]:20.307758092926903)[&state=\"0\"]:19.245199131205453, \"3768_0_119.44\"[&state=\"0\"]:16.339413793739027)[&state=\"0\"]:0.13200186375314615, \"3772_0_124.7\"[&state=\"0\"]:21.730378664192898)[&state=\"0\"]:54.3702578735728)[&state=\"0\"]:0.06069258906020991)[&state=\"0\"]:3.423386948468277)[&state=\"0\"]:17.359517818866983)[&state=\"2\"]:3.530482242672118, ((((\"3972_2_92.13\"[&state=\"2\"]:14.353178202215261, ((((\"4002_2_146.04\"[&state=\"2\"]:39.46271264901951, (\"4107_0_152.47\"[&state=\"0\"]:39.69490749020795)[&state=\"2\"]:6.198220313422311)[&state=\"2\"]:4.113916467014562, (\"4149_2_142.68\"[&state=\"2\"]:26.929435225380217, \"4245_2_142.89\"[&state=\"2\"]:27.14157247603535)[&state=\"2\"]:13.27984099001631)[&state=\"2\"]:1.0050667651936607, \"4364_2_159.61\"[&state=\"2\"]:58.151035533077405)[&state=\"2\"]:11.175725120926145, (\"4415_2_145.48\"[&state=\"2\"]:44.756772659641854, \"4420_2_119.15\"[&state=\"2\"]:18.427731149377323)[&state=\"2\"]:10.43739029484135)[&state=\"2\"]:12.51384251687277)[&state=\"2\"]:2.2705778507719145, \"4469_2_137.22\"[&state=\"2\"]:61.71610723912184)[&state=\"2\"]:11.79980245308483, (((\"4642_2_115.01\"[&state=\"2\"]:21.85198970647521, (((\"4700_2_158.78\"[&state=\"2\"]:21.133687269824378, \"4702_2_161.27\"[&state=\"2\"]:23.618182663373545)[&state=\"2\"]:30.84184680145627, (\"4748_1_157.13\"[&state=\"1\"]:22.55551801112219)[&state=\"2\"]:27.7673921973725)[&state=\"2\"]:4.1385785040901055, \"4806_2_127.68\"[&state=\"2\"]:25.013632545361844)[&state=\"2\"]:9.506799354834769)[&state=\"2\"]:10.162220840202863, (\"4835_2_148.48\"[&state=\"2\"]:51.89889418565204, \"4859_2_153.12\"[&state=\"2\"]:56.53901567559379)[&state=\"2\"]:13.583243324082545)[&state=\"2\"]:8.14027051813595, \"5095_2_96.58\"[&state=\"2\"]:21.71796443985268)[&state=\"2\"]:11.155209713060835)[&state=\"2\"]:19.61705023267109, ((\"5195_2_143.99\"[&state=\"2\"]:76.11722386026734, (\"5252_2_131.93\"[&state=\"2\"]:31.219993353292807, \"5261_2_119.45\"[&state=\"2\"]:18.74536911432199)[&state=\"2\"]:32.833014391526675)[&state=\"2\"]:18.1428467769913, (((\"5298_2_97.35\"[&state=\"2\"]:15.543133861141968, ((\"5377_2_156.02\"[&state=\"2\"]:59.96900378686186, (\"5528_2_130.39\"[&state=\"2\"]:29.368485452251974, (\"5574_2_161.53\"[&state=\"2\"]:38.884845442591455, \"5612_2_141.59\"[&state=\"2\"]:18.93954585438277)[&state=\"2\"]:21.632548897743703)[&state=\"2\"]:4.9628821182103025)[&state=\"2\"]:12.948935397902247, \"5626_2_143.38\"[&state=\"2\"]:60.27394401424179)[&state=\"2\"]:1.2993174998195087)[&state=\"2\"]:16.139350764934733, (((((\"5681_1_155.92\"[&state=\"1\"]:51.041027961310505, \"5869_1_159.67\"[&state=\"1\"]:54.792255434168084)[&state=\"1\"]:8.440707120534313, (\"5880_1_120.14\"[&state=\"1\"]:16.835250133974057, (((\"5893_1_145.88\"[&state=\"1\"]:35.42742269630013, \"5903_1_122.62\"[&state=\"1\"]:12.166427343939716)[&state=\"1\"]:0.4120118391681018, \"5976_1_142.8\"[&state=\"1\"]:32.7597069176698)[&state=\"1\"]:1.0054103606870086, \"5979_1_122.46\"[&state=\"1\"]:13.427556636274062)[&state=\"1\"]:5.735042929003399)[&state=\"1\"]:6.867405635386774)[&state=\"1\"]:3.9450424711902343, \"5982_1_103.77\"[&state=\"1\"]:11.278844020421701)[&state=\"1\"]:21.744904864410046)[&state=\"2\"]:1.7695561268901514, (((\"5996_0_130.7\"[&state=\"0\"]:16.425746987279553)[&state=\"2\"]:34.057986946781995, ((\"6044_1_153.69\"[&state=\"1\"]:26.423599602224726)[&state=\"2\"]:34.815197206135636, (\"6123_2_153.6\"[&state=\"2\"]:35.151979188017975, \"6183_2_139.67\"[&state=\"2\"]:21.22297792295393)[&state=\"2\"]:25.992993277055106)[&state=\"2\"]:12.23991331436092)[&state=\"2\"]:3.9590419164940016, (\"6361_2_143.16\"[&state=\"2\"]:48.673254141549805, \"6376_2_116.46\"[&state=\"2\"]:21.967207165701893)[&state=\"2\"]:18.238361885982812)[&state=\"2\"]:7.276863753623999)[&state=\"2\"]:3.3089235800246968)[&state=\"2\"]:4.321857866866637, ((\"6406_1_134.44\"[&state=\"1\"]:43.5325754137796)[&state=\"2\"]:14.922166101842478, (((((\"6543_0_153.86\"[&state=\"0\"]:52.951769081961956, ((((\"6687_0_155.84\"[&state=\"0\"]:26.976095106228485)[&state=\"1\"]:4.172599307627024)[&state=\"0\"]:11.407034550806202, \"6763_0_144.75\"[&state=\"0\"]:31.47427522908893)[&state=\"0\"]:4.622264755950283, \"6766_0_130.41\"[&state=\"0\"]:21.75148763873767)[&state=\"0\"]:7.754336146694428)[&state=\"0\"]:4.718016443353079)[&state=\"2\"]:7.810893423214878, (\"6810_2_138.35\"[&state=\"2\"]:38.67153872928502, \"6863_2_148.84\"[&state=\"2\"]:49.16432754546743)[&state=\"2\"]:11.302071822586058)[&state=\"2\"]:5.8690467106038255, ((\"6991_2_151.31\"[&state=\"2\"]:18.012601838877373, \"6993_2_159.35\"[&state=\"2\"]:26.05883475139788)[&state=\"2\"]:48.47239073451104, \"7082_2_116.45\"[&state=\"2\"]:31.62944565327028)[&state=\"2\"]:2.3146029874668983)[&state=\"2\"]:3.054600032232898, ((\"7098_2_142.9\"[&state=\"2\"]:17.59547072285781, \"7099_2_143.02\"[&state=\"2\"]:17.721284090349087)[&state=\"2\"]:0.36318611802565215, \"7101_2_150.58\"[&state=\"2\"]:25.642560955161684)[&state=\"2\"]:45.48620028457228)[&state=\"2\"]:3.4705518604132948)[&state=\"2\"]:14.635796870829125)[&state=\"2\"]:11.611416296820245)[&state=\"2\"]:5.648443221466536)[&state=\"2\"]:19.855630045885373)[&state=\"2\"]:24.229214360732055;";
//
//        ColouredTree colouredTree = new ColouredTree(newick, "state", 2, 3);
//
//        Tree tree = colouredTree.getFlattenedTree();
//        System.out.println(colouredTree.getTotalNumberofChanges());

    }

    @Override
    public void init(PrintStream printStream) throws Exception {

        printStream.println("#NEXUS\n");
        printStream.println("Begin trees;");
    }

    @Override
    public void log(int i, PrintStream printStream) {
        if (flatTree==null) getFlattenedTree();
        printStream.print("tree STATE_" + i + " = ");
        String sNewick = flatTree.getRoot().toNewick(null);
        printStream.print(sNewick);
        printStream.print(";");        
        
    }

    @Override
    public void close(PrintStream printStream) {
        printStream.print("End;");
    }


    public Boolean somethingIsDirty(){

        return changeColours.somethingIsDirty() || changeTimes.somethingIsDirty() || changeCounts.somethingIsDirty() ||
                nodeColours.somethingIsDirty();
        
    }
}
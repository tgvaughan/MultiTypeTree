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

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

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
	protected IntegerParameter nodeColours, changeColours, changeCounts;
    protected RealParameter changeTimes;

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
        treeParser.initByName("adjustTipHeights",false, "singlechild", true, "newick", newick);

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
        tree = treeParser;
        nNodes -= getSingleChildCount(treeParser, markedForDeletion) ;
        removeSingleChildren(markedForDeletion);

        assignColourArrays(cols, times, counts, nNodes);

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

		for (int n=0; n<nNodes; n++) {
			changeCounts.setValue(n, 0);
			nodeColours.setValue(n, 0);
		}
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
	public void removeSingleChildren(List<Integer> markedForDeletion) throws Exception {

        List<Node> nodes= new ArrayList<Node>();

        for (int i : markedForDeletion){
            nodes.add( tree.getNode(i));
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

        tree.nodeCount = tree.getNodeCount() - nodes.size();
        tree.internalNodeCount = tree.getInternalNodeCount() - nodes.size();

        tree.getRoot().labelInternalNodes(tree.getLeafNodeCount());
        tree.setInputValue("newick", null);

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

                for (int j=0; j<=i; j++){
                    addChange(nodeNr, maxBranchColours*nodeNr+counts[nodeNr], changes[i-j], changetimes[i-j], cols, times, counts);
                    // todo: ask Tim if the order matters here
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

    private void assignColourArrays(Integer[] cols, Double[] times, Integer[] counts, int nNodes) throws Exception{


        Integer[] cols_afterRemoval = new Integer[nNodes*maxBranchColours]; Arrays.fill(cols_afterRemoval,-1);
        Double[] times_afterRemoval = new Double[nNodes*maxBranchColours];  Arrays.fill(times_afterRemoval,-1.);
        Integer[] counts_afterRemoval =  new Integer[nNodes]; Arrays.fill(counts_afterRemoval,0);
        Integer[] nodeCols = new Integer[nNodes];

        Node node;
        int oldNodeNumber;

        for (int i =0; i<nNodes; i++){
            node = tree.getNode(i);

			nodeCols[i] = Integer.parseInt(node.m_sMetaData
					.split("=")[1]
					.replaceAll("'","")
					.replaceAll("\"",""));

            oldNodeNumber = (Integer) node.getMetaData("oldNodeNumber");
            counts_afterRemoval[i] = counts[oldNodeNumber];

            for (int j=0; j<counts_afterRemoval[i]; j++){

                cols_afterRemoval[maxBranchColours*i+j] = cols[maxBranchColours*oldNodeNumber+j];
                times_afterRemoval[maxBranchColours*i+j] = times[maxBranchColours*oldNodeNumber+j];
            }
        }

        changeColours = new IntegerParameter(cols_afterRemoval);
        changeTimes = new RealParameter(times_afterRemoval);
        changeCounts = new IntegerParameter(counts_afterRemoval);
        nodeColours = new IntegerParameter(nodeCols);

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
        Tree flatTree = tree.copy();
        flatTree.initArrays();

        int nextNodeNr = tree.getNodeCount();

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
        String newick = "(((1[&state='1']:1, (2[&state='0']:.5)[&state='1']:1.5)[&state='1']:2)[&state='0']:1, (3[&state='0']:1.5, (4[&state='1']:1.5)[&state='0']:1 )[&state='0']:2)[&state='0']:1;";

        ColouredTree colouredTree = new ColouredTree(newick, "state", 2, 3);

        Tree tree = colouredTree.getFlattenedTree();
        System.out.println(colouredTree.getFinalBranchTime(tree.getRoot()));

    }

}
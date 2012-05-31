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
        treeParser.initByName("adjustTipHeights",false, "singlechild", true, "newick", newick);

        this.colourLabel = colourLabel;
        this.nColours = nColours;
        this.maxBranchColours = maxBranchColours;


        List<Integer> markedForDeletion = new ArrayList<Integer>();
        int nBranches = treeParser.getNodeCount() - 1 ;//

        Integer[] cols = new Integer[nBranches*maxBranchColours]; Arrays.fill(cols,-1);
        Double[] times = new Double[nBranches*maxBranchColours];  Arrays.fill(times,-1.);
        Integer[]  counts =  new Integer[nBranches]; Arrays.fill(counts,0);

        setupColourParameters(treeParser.getRoot(),cols, times, counts);

        // Assign parsed tree to official tree topology field and remove single children from it:
        tree = treeParser;
        nBranches -= getSingleChildCount(treeParser, markedForDeletion) ;
        removeSingleChildren(markedForDeletion);

        assignColourArrays(cols, times, counts, nBranches);


        // Allocate and initialise array for lazily recording
        // final colour of each branch:
        finalColours = new Integer[nBranches];
        finalColoursDirty = new Boolean[nBranches];
        for (int i=0; i<nBranches; i++) {
            finalColours[i] = 0;
            finalColoursDirty[i] = true;
        }

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

            Node parent = node.getParent();

            parent.children = node.getChildren();

            for (Node child : parent.children){
                child.setParent(parent);
            }
        }

        tree.setInputValue("newick", null);

    }

    /**
     * Set up colours for newick constructor .
     *
     * @param node
     * @param cols
     * @param times
     * @param counts
     */
    private void setupColourParameters(Node node, Integer[] cols, Double[] times, Integer[] counts){//, Integer[] leafCols){

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

                setupColourParameters(node ,cols, times, counts);//, leafCols);


            } else {

                nodeNr = child1.getNr();
                if (child1state!=nodeState)
                    addChange(nodeNr, maxBranchColours*nodeNr+counts[nodeNr], nodeState, node.getHeight(), cols, times, counts);

                setupColourParameters(child1 ,cols, times, counts);//, leafCols);
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

            setupColourParameters(child1 ,cols, times, counts);//, leafCols);
            setupColourParameters(child2 ,cols, times, counts);//, leafCols);

        }

//        else if (node.isLeaf()){
//            leafCols[nodeNr] = nodeState;
//        }

    }

    private void assignColourArrays(Integer[] cols, Double[] times, Integer[] counts, int nBranches) throws Exception{


        Integer[] cols_afterRemoval = new Integer[nBranches*maxBranchColours]; Arrays.fill(cols_afterRemoval,-1);
        Double[] times_afterRemoval = new Double[nBranches*maxBranchColours];  Arrays.fill(times_afterRemoval,-1.);
        Integer[] counts_afterRemoval =  new Integer[nBranches]; Arrays.fill(counts_afterRemoval,0);
        Integer[] leafCols = new Integer[tree.getLeafNodeCount()];

        Node node;
        int oldNodeNumber;

        for (int i =0; i<nBranches; i++){
            node = tree.getNode(i);

            if (node.isLeaf())
                leafCols[i] = Integer.parseInt(node.m_sMetaData.split("=")[1].replaceAll("'","").replaceAll("\"",""));

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
        leafColours = new IntegerParameter(leafCols);

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
            throw new IllegalArgumentException("Argument to getFinalBranchColour "
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


    public static void main(String[] args) throws Exception{



        // 4 taxa test tree:
        String newick = "(((1[&state='1']:1, (2[&state='0']:.5)[&state='1']:1.5)[&state='1']:2)[&state='0']:1, (3[&state='0']:1.5, (4[&state='1']:1.5)[&state='0']:1 )[&state='0']:2)[&state='0']:1;";

        // 100 taxa test tree:

//            String newick =" (((((((\"216_0_105.83\"[&state=\"0\"]:28.771977280210464, (\"343_0_147.99\"[&state=\"0\"]:64.96763642217283, \"392_0_166.73\"[&state=\"0\"]:83.7056007591273)[&state=\"0\"]:5.962532597025245)[&state=\"0\"]:21.807593094666487, (\"623_0_112.72\"[&state=\"0\"]:45.390415190303386, \"673_0_87.71\"[&state=\"0\"]:20.378326633337522)[&state=\"0\"]:12.081384556369898)[&state=\"0\"]:7.095479064167385, (\"699_0_98.05\"[&state=\"0\"]:35.875760300023444, \"710_0_107.73\"[&state=\"0\"]:45.5625524982232)[&state=\"0\"]:14.012876570744062)[&state=\"0\"]:21.14440426806084, (((\"804_0_111.52\"[&state=\"0\"]:51.26454621990025, ((((\"935_0_125.26\"[&state=\"0\"]:23.786912640445323, \"938_0_131.41\"[&state=\"0\"]:29.941708603448774)[&state=\"0\"]:37.94424586281844, (\"1065_0_133.28\"[&state=\"0\"]:61.47018901874536, (((\"1114_0_173.1\"[&state=\"0\"]:88.734385512821, \"1155_0_131.39\"[&state=\"0\"]:47.02736826092948)[&state=\"0\"]:8.030508878206135, \"1207_0_115.18\"[&state=\"0\"]:38.84873210218484)[&state=\"0\"]:1.3856178623677948, ((\"1242_0_136.0\"[&state=\"0\"]:43.33703974098212, (\"1281_0_175.51\"[&state=\"0\"]:76.42786535823336, \"1342_0_142.26\"[&state=\"0\"]:43.174171604761455)[&state=\"0\"]:6.418192948835795)[&state=\"0\"]:6.263459153561584, \"1347_0_114.33\"[&state=\"0\"]:27.928348007822336)[&state=\"0\"]:11.45582942270184)[&state=\"0\"]:3.1375877347302463)[&state=\"0\"]:8.282391183389642)[&state=\"0\"]:1.29451646842314, \"1359_0_107.49\"[&state=\"0\"]:45.252391392272834)[&state=\"0\"]:1.6039683074347835, (\"1382_0_91.33\"[&state=\"0\"]:29.47193465033193, \"1388_0_76.51\"[&state=\"0\"]:14.649816852734517)[&state=\"0\"]:1.2324415409658585)[&state=\"0\"]:0.3778755113478667)[&state=\"0\"]:12.421987637170936, (\"1429_0_72.82\"[&state=\"0\"]:13.322072203069787, ((\"1431_0_102.12\"[&state=\"0\"]:23.195581152395775, \"1744_0_136.6\"[&state=\"0\"]:57.676704951762616)[&state=\"0\"]:15.404711667852993, (\"1795_0_149.07\"[&state=\"0\"]:66.09569016146419, \"1798_0_115.66\"[&state=\"0\"]:32.6900298153066)[&state=\"0\"]:19.451407963260152)[&state=\"0\"]:4.023322910553695)[&state=\"0\"]:11.668511550401469)[&state=\"0\"]:16.400514940460315, (((\"1835_0_90.78\"[&state=\"0\"]:17.50739519046961, (((\"1847_0_133.32\"[&state=\"0\"]:38.814467957221765, \"1854_0_143.81\"[&state=\"0\"]:49.31301527702736)[&state=\"0\"]:3.482519552660193, ((\"2030_1_169.43\"[&state=\"1\"]:22.884347636952697, \"2047_1_172.73\"[&state=\"1\"]:26.185303807869644)[&state=\"1\"]:33.370694700899406)[&state=\"0\"]:22.159763219296806)[&state=\"0\"]:8.117353054905706, \"2126_0_116.46\"[&state=\"0\"]:33.55524142321835)[&state=\"0\"]:9.62650424861188)[&state=\"0\"]:21.099175966626483, (\"2235_0_78.65\"[&state=\"0\"]:26.180608059693242, \"2248_0_81.93\"[&state=\"0\"]:29.456805781326025)[&state=\"0\"]:0.29801468179062596)[&state=\"0\"]:20.52764025668319, ((\"2417_0_108.71\"[&state=\"0\"]:31.198505402850273, (\"2432_0_168.47\"[&state=\"0\"]:75.67086414394095, \"2473_0_114.26\"[&state=\"0\"]:21.461568479130563)[&state=\"0\"]:15.289858690795441)[&state=\"0\"]:12.083275434065172, (\"2512_0_124.43\"[&state=\"0\"]:57.70789585948346, (\"2671_0_125.55\"[&state=\"0\"]:54.34965593804053, \"2679_0_83.97\"[&state=\"0\"]:12.770963117912203)[&state=\"0\"]:4.477209939512022)[&state=\"0\"]:1.292312936462153)[&state=\"0\"]:33.78108843309889)[&state=\"0\"]:0.21922118940199908)[&state=\"0\"]:4.416155762876404)[&state=\"0\"]:5.718319568014781, \"2692_0_60.48\"[&state=\"0\"]:39.182123455821156)[&state=\"0\"]:2.664522929873929, ((\"2854_0_137.76\"[&state=\"0\"]:43.117258983481676, \"2880_0_152.12\"[&state=\"0\"]:57.468745544995656)[&state=\"0\"]:71.4955906873979, (((\"3024_0_108.22\"[&state=\"0\"]:30.071230359952267, (\"3063_0_174.37\"[&state=\"0\"]:62.174505583978785, \"3086_0_158.29\"[&state=\"0\"]:46.090991846334376)[&state=\"0\"]:34.045349289728705)[&state=\"0\"]:26.305969594194877, \"3219_0_91.4\"[&state=\"0\"]:39.55698332778984)[&state=\"0\"]:8.725122528114895, \"3366_0_53.19\"[&state=\"0\"]:10.072859477257971)[&state=\"0\"]:19.969190146244816)[&state=\"0\"]:4.521334546466672)[&state=\"0\"]:13.041755583822052, ((((((((((\"3456_2_158.72\"[&state=\"2\"]:24.186937201743547, \"3477_2_170.24\"[&state=\"2\"]:35.70426583098612)[&state=\"2\"]:1.2612824115472563)[&state=\"0\"]:56.32137082112472, (\"3700_2_168.07\"[&state=\"2\"]:81.68896715597695)[&state=\"0\"]:9.426361155574966)[&state=\"0\"]:2.732135878606954, \"3847_0_105.32\"[&state=\"0\"]:31.097398582811792)[&state=\"0\"]:4.281659351082425, \"3875_0_146.28\"[&state=\"0\"]:76.34000145536773)[&state=\"0\"]:12.372923764332853, \"3940_0_75.75\"[&state=\"0\"]:18.18239780793766)[&state=\"0\"]:10.576996345455427, \"3949_0_97.32\"[&state=\"0\"]:50.333494908482294)[&state=\"0\"]:5.806111000680623, (((\"3969_0_138.72\"[&state=\"0\"]:65.60963700250004, \"3973_0_89.13\"[&state=\"0\"]:16.024077280302706)[&state=\"0\"]:5.4142025112991945, \"4147_0_116.15\"[&state=\"0\"]:48.45391156005475)[&state=\"0\"]:17.644492475044153, \"4178_0_105.32\"[&state=\"0\"]:55.2711388012598)[&state=\"0\"]:8.863419276688873)[&state=\"0\"]:5.015509786906058, ((((\"4267_0_122.93\"[&state=\"0\"]:72.80507095407945, \"4299_0_70.9\"[&state=\"0\"]:20.77937559811869)[&state=\"0\"]:4.998844667856034, ((\"4317_0_132.03\"[&state=\"0\"]:35.9308102450744, \"4336_0_171.82\"[&state=\"0\"]:75.71804604832319)[&state=\"0\"]:27.90738917758948, (\"4439_0_158.82\"[&state=\"0\"]:85.41137508232269, ((\"4584_0_130.54\"[&state=\"0\"]:32.09231481246263, \"4592_0_140.4\"[&state=\"0\"]:41.94495287384183)[&state=\"0\"]:2.9540838415753257, \"4597_0_128.36\"[&state=\"0\"]:32.858735435381035)[&state=\"0\"]:22.087557615365398)[&state=\"0\"]:5.215944835472428)[&state=\"0\"]:23.07080442325318)[&state=\"0\"]:4.080258610990249, (((\"4665_0_76.83\"[&state=\"0\"]:16.308425841875163, (((\"4750_0_143.92\"[&state=\"0\"]:57.175569549527225, \"4846_0_165.32\"[&state=\"0\"]:78.57039091549812)[&state=\"0\"]:13.162331646217936, ((\"5062_0_144.97\"[&state=\"0\"]:39.888379895279556, \"5064_0_126.91\"[&state=\"0\"]:21.825998923520814)[&state=\"0\"]:20.29641666199295, (\"5068_0_154.04\"[&state=\"0\"]:64.21058536970061, \"5109_0_108.89\"[&state=\"0\"]:19.066790438165228)[&state=\"0\"]:5.037845840742719)[&state=\"0\"]:11.203864590884649)[&state=\"0\"]:11.569433877413083, \"5117_0_83.67\"[&state=\"0\"]:21.65418121768311)[&state=\"0\"]:1.4946484323150102)[&state=\"0\"]:4.099139410439932, (((\"5151_0_99.64\"[&state=\"0\"]:16.899940176598804, \"5201_0_157.33\"[&state=\"0\"]:74.59002156220181)[&state=\"0\"]:5.155344950640838, \"5302_0_120.84\"[&state=\"0\"]:43.257691317049094)[&state=\"0\"]:14.05941431718255, \"5304_0_96.89\"[&state=\"0\"]:33.370633854601685)[&state=\"0\"]:7.099708817642529)[&state=\"0\"]:7.081921553705271, (((\"5357_0_91.99\"[&state=\"0\"]:17.135562101655324, (\"5429_0_141.84\"[&state=\"0\"]:55.13360535644719, \"5448_0_144.77\"[&state=\"0\"]:58.062742683473715)[&state=\"0\"]:11.849978051741147)[&state=\"0\"]:8.36222931703044, \"5482_0_86.8\"[&state=\"0\"]:20.31350288685303)[&state=\"0\"]:12.228020731390565, (\"5491_0_83.87\"[&state=\"0\"]:23.275164682889233, ((\"5542_0_132.07\"[&state=\"0\"]:26.8129364503707, \"5545_0_139.09\"[&state=\"0\"]:33.84187727651445)[&state=\"0\"]:15.308691950965738, (\"5574_0_123.86\"[&state=\"0\"]:28.84376975861504, \"5586_0_129.32\"[&state=\"0\"]:34.30802617330515)[&state=\"0\"]:5.068072942567866)[&state=\"0\"]:29.345258044728702)[&state=\"0\"]:6.336450504539286)[&state=\"0\"]:4.9229948491425475)[&state=\"0\"]:8.295518711635516)[&state=\"0\"]:0.5666530881693532, (\"5611_0_82.01\"[&state=\"0\"]:24.139896011666252, \"5683_0_120.0\"[&state=\"0\"]:62.130028898740434)[&state=\"0\"]:17.38826232600752)[&state=\"0\"]:4.307879706931075)[&state=\"0\"]:9.108778511052005, ((((\"5750_0_111.5\"[&state=\"0\"]:32.40266666821623, \"5921_0_118.9\"[&state=\"0\"]:39.80291802871788)[&state=\"0\"]:3.7483419386554147, \"5936_0_124.42\"[&state=\"0\"]:49.07118051517655)[&state=\"0\"]:14.782850096312806, \"5965_0_85.12\"[&state=\"0\"]:24.548650027693206)[&state=\"0\"]:9.626317596192102, ((\"6060_0_122.39\"[&state=\"0\"]:42.8814802179279, (\"6080_0_175.29\"[&state=\"0\"]:85.95127080757932, (\"6093_0_134.2\"[&state=\"0\"]:44.674161069980826, \"6157_0_137.69\"[&state=\"0\"]:48.165138326949986)[&state=\"0\"]:0.19236571893858923)[&state=\"0\"]:9.832840141720013)[&state=\"0\"]:9.081529645809013, (\"6178_0_130.81\"[&state=\"0\"]:46.948974028094256, \"6194_0_110.74\"[&state=\"0\"]:26.879507201132427)[&state=\"0\"]:13.435927370145862)[&state=\"0\"]:19.478529566704438)[&state=\"0\"]:23.883481661911613)[&state=\"0\"]:21.472647481136026)[&state=\"0\"]:5.58777462123409;";
        ColouredTree colouredTree = new ColouredTree(newick, "state", 2, 3);

        Tree tree = colouredTree.getFlattenedTree();
        System.out.println(colouredTree.getFinalBranchTime(tree.getRoot()));



    }

}

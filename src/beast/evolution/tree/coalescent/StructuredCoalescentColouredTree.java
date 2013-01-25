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
package beast.evolution.tree.coalescent;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.State;
import beast.core.StateNode;
import beast.core.StateNodeInitialiser;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.evolution.migrationmodel.MigrationModel;
import beast.evolution.operators.ColouredTreeModifier;
import beast.evolution.tree.ColouredTree;
import beast.evolution.tree.Node;
import beast.evolution.tree.TraitSet;
import beast.evolution.tree.Tree;
import beast.math.statistic.DiscreteStatistics;
import beast.util.Randomizer;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A coloured tree generated randomly from leaf colours and a migration matrix
 * with fixed population sizes.
 *
 * @author Tim Vaughan
 */
@Description("A coloured tree generated randomly from leaf colours and"
+ "a migration matrix with fixed population sizes.")
public class StructuredCoalescentColouredTree extends ColouredTree implements StateNodeInitialiser {

    /*
     * Plugin inputs:
     */
    public Input<MigrationModel> migrationModelInput = new Input<MigrationModel>(
            "migrationModel",
            "Migration model to use in simulator.",
            Validate.REQUIRED);
    public Input<IntegerParameter> leafColoursInput = new Input<IntegerParameter>(
            "leafColours",
            "Colours of leaf nodes.");
    public Input<TraitSet> colourTraitSetInput = new Input<TraitSet>(
            "colourTraitSet",
            "Trait set specifying colours of leaf nodes.");
    public Input<TraitSet> timeTraitSetInput = new Input<TraitSet>(
            "timeTraitSet",
            "Trait set specifying ages of leaf nodes.");
    /*
     * Non-input fields:
     */
    protected MigrationModel migrationModel;
    
    private ColouredTreeModifier modifier;
    
    private List<Integer> leafColours;
    private List<String> leafNames;
    private List<Double> leafTimes;
    private int nLeaves;


    /*
     * Other private fields and classes:
     */
    private abstract class SCEvent {

        double time;
        int fromColour, toColour;

        boolean isCoalescence() {
            return true;
        }
    }

    private class CoalescenceEvent extends SCEvent {

        public CoalescenceEvent(int colour, double time) {
            this.fromColour = colour;
            this.time = time;
        }
    }

    private class MigrationEvent extends SCEvent {

        public MigrationEvent(int fromColour, int toColour, double time) {
            this.fromColour = fromColour;
            this.toColour = toColour;
            this.time = time;
        }
    }
    
    private class NullEvent extends SCEvent {
        public NullEvent() {
            this.time = Double.POSITIVE_INFINITY;
        }
    }
    
    public StructuredCoalescentColouredTree() { }

    @Override
    public void initAndValidate() throws Exception {
        
        super.initAndValidate();
        
        // Get modifier instance
        modifier = new ColouredTreeModifier(this);

        // Obtain required parameters from inputs:
        nColours = nColoursInput.get();
        colourLabel = colourLabelInput.get();
        maxBranchColours = maxBranchColoursInput.get();
        migrationModel = migrationModelInput.get();

        // Obtain leaf colours from explicit input or alignment:
        leafColours = Lists.newArrayList();
        leafNames = Lists.newArrayList();
        if (leafColoursInput.get() != null) {            
            for (int i=0; i<leafColoursInput.get().getDimension(); i++) {
                leafColours.add(leafColoursInput.get().getValue(i));
                leafNames.add(String.valueOf(i));
            }
        } else {
            if (colourTraitSetInput.get() == null)
                throw new IllegalArgumentException("Either leafColours or "
                        + "trait set must be provided.");

            // Fill leaf colour array:
            for (int i = 0; i<colourTraitSetInput.get().m_taxa.get().asStringList().size(); i++) {
                leafColours.add((int)colourTraitSetInput.get().getValue(i));
                leafNames.add(colourTraitSetInput.get().m_taxa.get().asStringList().get(i));
            }
        }
        
        nLeaves = leafColours.size();
        
        // Set leaf times if specified:
        leafTimes = Lists.newArrayList();
        if (timeTraitSetInput.get() == null) {
            for (int i=0; i<nLeaves; i++)
                leafTimes.add(0.0);
        } else {
            if (timeTraitSetInput.get().m_taxa.get().asStringList().size() != nLeaves)
                throw new IllegalArgumentException("Number of time traits "
                        + "doesn't match number of leaf colours supplied.");
            
            for (int i=0; i<nLeaves; i++)
                leafTimes.add(timeTraitSetInput.get().getValue(i));
        }
        
        // Hack to deal with StateNodes that haven't been attached to
        // a state yet.
        if (changeColours.getState() == null) {
            State state = new State();
            state.initByName(
                    "stateNode", changeColours,
                    "stateNode", changeTimes,
                    "stateNode", changeCounts,
                    "stateNode", nodeColours);
            state.initialise();
        }

        // Allocate arrays for recording colour change information:
        int nNodes = 2 * leafColours.size() - 1;
        initParameters(nNodes);

        // Construct tree and assign to input plugin:
        tree.assignFromWithoutID(new Tree(simulateTree()));

        // Ensure colouring is internally consistent:
        if (!isValid())
            throw new Exception("Inconsistent colour assignment.");
    }

    /**
     * Generates tree using the specified list of active leaf nodes using the
     * structured coalescent.
     *
     * @param activeNodes
     * @return Root node of generated tree.
     */
    private Node simulateTree() throws Exception {

        // Initialise node creation counter:
        int nextNodeNr = 0;

        // Initialise node lists:
        List<List<Node>> activeNodes = Lists.newArrayList();
        List<List<Node>> inactiveNodes = Lists.newArrayList();
        for (int i = 0; i < nColours; i++) {
            activeNodes.add(new ArrayList<Node>());
            inactiveNodes.add(new ArrayList<Node>());
        }

        // Add nodes to inactive nodes list:
        for (int l = 0; l < nLeaves; l++) {
            Node node = new Node();
            node.setNr(nextNodeNr);
            node.setID(leafNames.get(l));
            inactiveNodes.get(leafColours.get(l)).add(node);
            modifier.setNodeHeight(node, leafTimes.get(l));
            modifier.setNodeColour(node, leafColours.get(l));

            nextNodeNr++;
        }
        
        // Sort nodes in inactive nodes lists in order of increasing age:
        for (int i=0; i<nColours; i++) {
            Collections.sort(inactiveNodes.get(i), new Comparator<Node>() {
                @Override
                public int compare(Node node1, Node node2) {
                    double dt = node1.getHeight()-node2.getHeight();
                    if (dt<0)
                        return -1;
                    if (dt>0)
                        return 1;
                    
                    return 0;
                }
            });
        }

        // Allocate propensity lists:
        List<List<Double>> migrationProp = new ArrayList<List<Double>>();
        List<Double> coalesceProp = new ArrayList<Double>();
        for (int i = 0; i < migrationModel.getNDemes(); i++) {
            coalesceProp.add(0.0);
            migrationProp.add(new ArrayList<Double>());
            for (int j = 0; j < migrationModel.getNDemes(); j++)
                migrationProp.get(i).add(0.0);
        }

        double t = 0.0;

        while (totalNodesRemaining(activeNodes)>1
                || totalNodesRemaining(inactiveNodes)>0) {

            // Step 1: Calculate propensities.
            double totalProp = updatePropensities(migrationProp, coalesceProp,
                    activeNodes);
            
            // Step 2: Determine next event.
            SCEvent event = getNextEvent(migrationProp, coalesceProp,
                    totalProp, t);

            // Step 3: Handle activation of nodes:
            Node nextNode = null;
            int nextNodeCol = -1;
            double nextTime = Double.POSITIVE_INFINITY;
            for (int i=0; i<nColours; i++) {
                if (inactiveNodes.get(i).isEmpty())
                    continue;
                
                if (inactiveNodes.get(i).get(0).getHeight()<nextTime) {
                    nextNode = inactiveNodes.get(i).get(0);
                    nextTime = nextNode.getHeight();
                    nextNodeCol = i;
                }
            }
            if (nextTime < event.time) {
                t = nextTime;
                activeNodes.get(nextNodeCol).add(nextNode);
                inactiveNodes.get(nextNodeCol).remove(0);
                continue;
            }
            
            // Step 4: Place event on tree.
            nextNodeNr = updateTree(activeNodes, event, nextNodeNr);

            // Step 5: Keep track of time increment.
            t = event.time;
        }

        // Return sole remaining active node as root:
        for (List<Node> nodeList : activeNodes)
            if (!nodeList.isEmpty())
                return nodeList.get(0);

        // Should not fall through.
        throw new Exception("No active nodes remaining end of "
                + "structured coalescent simulation!");
    }

    /**
     * Obtain propensities (instantaneous reaction rates) for coalescence and
     * migration events.
     *
     * @param migrationProp
     * @param coalesceProp
     * @param activeNodes
     * @return Total reaction propensity.
     */
    private double updatePropensities(List<List<Double>> migrationProp,
            List<Double> coalesceProp, List<List<Node>> activeNodes) {

        double totalProp = 0.0;

        for (int i = 0; i < migrationProp.size(); i++) {

            double N = migrationModel.getPopSize(i);
            int k = activeNodes.get(i).size();

            coalesceProp.set(i, k * (k - 1) / (2.0 * N));
            totalProp += coalesceProp.get(i);

            for (int j = 0; j < migrationProp.size(); j++) {

                if (j == i)
                    continue;

                double m = migrationModel.getBackwardRate(j, i);

                migrationProp.get(i).set(j, k * m);
                totalProp += migrationProp.get(i).get(j);
            }
        }

        return totalProp;

    }

    /**
     * Calculate total number of active nodes remaining.
     *
     * @param activeNodes
     * @return Number of active nodes remaining.
     */
    private int totalNodesRemaining(List<List<Node>> activeNodes) {
        int result = 0;

        for (List<Node> nodeList : activeNodes)
            result += nodeList.size();

        return result;
    }

    /**
     * Obtain type and location of next reaction.
     *
     * @param migrateProp Current migration propensities.
     * @param coalesceProp Current coalescence propensities.
     * @param t Current time.
     * @return Event object describing next event.
     */
    private SCEvent getNextEvent(List<List<Double>> migrateProp,
            List<Double> coalesceProp, double totalProp, double t)
            throws Exception {

        // Get time of next event:
        if (totalProp>0.0)
            t += Randomizer.nextExponential(totalProp);
        else
            return new NullEvent();

        // Select event type:
        double U = Randomizer.nextDouble() * totalProp;
        for (int i = 0; i < migrateProp.size(); i++) {

            if (U < coalesceProp.get(i))
                return new CoalescenceEvent(i, t);
            else
                U -= coalesceProp.get(i);

            for (int j = 0; j < migrateProp.size(); j++) {

                if (j == i)
                    continue;

                if (U < migrateProp.get(i).get(j))
                    return new MigrationEvent(i, j, t);
                else
                    U -= migrateProp.get(i).get(j);
            }
        }

        // Loop should not fall through.
        throw new Exception("Structured coalescenct event selection error.");

    }

    /**
     * Update tree with result of latest event.
     *
     * @param activeNodes
     * @param event
     * @param nextNodeNr Integer identifier of last node added to tree.
     * @return Updated nextNodeNr.
     */
    private int updateTree(List<List<Node>> activeNodes, SCEvent event,
            int nextNodeNr) {

        if (event instanceof CoalescenceEvent) {

            // Randomly select node pair with chosen colour:
            Node daughter = selectRandomNode(activeNodes.get(event.fromColour));
            Node son = selectRandomSibling(
                    activeNodes.get(event.fromColour), daughter);

            // Create new parent node with appropriate ID and time:
            Node parent = new Node();
            parent.setNr(nextNodeNr);
            parent.setID(String.valueOf(nextNodeNr));
            modifier.setNodeHeight(parent, event.time);
            nextNodeNr++;

            // Connect new parent to children:
            modifier.setNodeChildLeft(parent, daughter);
            modifier.setNodeChildRight(parent, son);
            modifier.setNodeParent(son, parent);
            modifier.setNodeParent(daughter, parent);

            // Ensure new parent is set to correct colour:
            modifier.setNodeColour(parent, event.fromColour);

            // Update activeNodes:
            activeNodes.get(event.fromColour).remove(son);
            int idx = activeNodes.get(event.fromColour).indexOf(daughter);
            activeNodes.get(event.fromColour).set(idx, parent);

        } else {

            // Randomly select node with chosen colour:
            Node migrator = selectRandomNode(activeNodes.get(event.fromColour));

            // Record colour change in change lists:
            modifier.addChange(migrator, event.toColour, event.time);

            // Update activeNodes:
            activeNodes.get(event.fromColour).remove(migrator);
            activeNodes.get(event.toColour).add(migrator);

        }

        return nextNodeNr;

    }

    /**
     * Use beast RNG to select random node from list.
     *
     * @param nodeList
     * @return A randomly selected node.
     */
    private Node selectRandomNode(List<Node> nodeList) {
        return nodeList.get(Randomizer.nextInt(nodeList.size()));
    }

    /**
     * Return random node from list, excluding given node.
     *
     * @param nodeList
     * @param node
     * @return Randomly selected node.
     */
    private Node selectRandomSibling(List<Node> nodeList, Node node) {

        int n = Randomizer.nextInt(nodeList.size() - 1);
        int idxToAvoid = nodeList.indexOf(node);
        if (n >= idxToAvoid)
            n++;

        return nodeList.get(n);
    }

        
    @Override
    public void initStateNodes() { }

    @Override
    public List<StateNode> getInitialisedStateNodes() {
        List<StateNode> statenodes = new ArrayList<StateNode>();

        statenodes.add(treeInput.get());
        statenodes.add(changeColoursInput.get());
        statenodes.add(changeTimesInput.get());
        statenodes.add(changeCountsInput.get());
        statenodes.add(nodeColoursInput.get());

        return statenodes;
    }    
    
    /**
     * Generates an ensemble of trees from the structured coalescent for testing
     * coloured tree-space samplers.
     *
     * @param argv
     */
    public static void main(String[] argv) throws Exception {

        // Set up migration model.
        RealParameter rateMatrix = new RealParameter();
        rateMatrix.initByName(
                "dimension", 4,
                "minordimension", 2,
                "value", "0.0 0.1 0.1 0.0");
        RealParameter popSizes = new RealParameter();
        popSizes.initByName(
                "dimension", 2,
                "value", "7.0 7.0");
        MigrationModel migrationModel = new MigrationModel();
        migrationModel.initByName(
                "rateMatrix", rateMatrix,
                "popSizes", popSizes);

        // Specify leaf colours:
        IntegerParameter leafColours = new IntegerParameter();
        leafColours.initByName(
                "dimension", 2,
                "value", "0 0");

        // Generate ensemble:
        int reps = 100000;
        double[] heights = new double[reps];

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < reps; i++) {

            if (i % 1000 == 0)
                System.out.format("%d reps done\n", i);

            StructuredCoalescentColouredTree sctree;
            sctree = new StructuredCoalescentColouredTree();
            sctree.initByName(
                    "migrationModel", migrationModel,
                    "leafColours", leafColours,
                    "nColours", 2,
                    "maxBranchColours", 50);

            heights[i] = sctree.getUncolouredTree().getRoot().getHeight();
        }

        long time = System.currentTimeMillis() - startTime;

        System.out.printf("E[T] = %1.4f +/- %1.4f\n",
                DiscreteStatistics.mean(heights), DiscreteStatistics.stdev(heights) / Math.sqrt(reps));
        System.out.printf("V[T] = %1.4f\n", DiscreteStatistics.variance(heights));

        System.out.printf("Took %1.2f seconds\n", time / 1000.0);

        /*
        PrintStream outStream = new PrintStream(new File("heights.txt"));
        outStream.println("h");
        for (int i = 0; i < reps; i++)
            outStream.println(heights[i]);
        outStream.close();
        */

    }

}

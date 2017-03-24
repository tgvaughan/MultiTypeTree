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

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.StateNode;
import beast.core.StateNodeInitialiser;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.math.statistic.DiscreteStatistics;
import beast.util.Randomizer;
import com.google.common.collect.Lists;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.*;

/**
 * A multi-type tree generated randomly from leaf types and a migration matrix
 * with fixed population sizes.
 *
 * @author Tim Vaughan
 */
@Description("A multi-type tree generated randomly from leaf types and"
+ "a migration matrix with fixed population sizes.")
public class StructuredCoalescentMultiTypeTree extends MultiTypeTree implements StateNodeInitialiser {

    /*
     * Plugin inputs:
     */
    public Input<SCMigrationModel> migrationModelInput = new Input<>(
            "migrationModel",
            "Migration model to use in simulator.",
            Validate.REQUIRED);
    
    public Input<IntegerParameter> leafTypesInput = new Input<>(
            "leafTypes",
            "Types of leaf nodes.");
    
    public Input<String> outputFileNameInput = new Input<>(
            "outputFileName", "Optional name of file to write simulated "
                    + "tree to.");

    /*
     * Non-input fields:
     */
    protected SCMigrationModel migModel;
    
    private List<Integer> leafTypes;
    private List<String> leafNames;
    private List<Double> leafTimes;
    private int nLeaves;

    /*
     * Other private fields and classes:
     */
    private abstract class SCEvent {

        double time;
        int fromType, toType;

        boolean isCoalescence() {
            return true;
        }
    }

    private class CoalescenceEvent extends SCEvent {

        public CoalescenceEvent(int type, double time) {
            this.fromType = type;
            this.time = time;
        }
    }

    private class MigrationEvent extends SCEvent {

        public MigrationEvent(int fromType, int toType, double time) {
            this.fromType = fromType;
            this.toType = toType;
            this.time = time;
        }
    }
    
    private class NullEvent extends SCEvent {
        public NullEvent() {
            this.time = Double.POSITIVE_INFINITY;
        }
    }
    
    public StructuredCoalescentMultiTypeTree() { }

    @Override
    public void initAndValidate() {
        
        super.initAndValidate();

        // Obtain required parameters from inputs:
        migModel = migrationModelInput.get();

        // Obtain leaf colours from explicit input or alignment:
        leafTypes = Lists.newArrayList();
        leafNames = Lists.newArrayList();
        if (leafTypesInput.get() != null) {
            for (int i=0; i<leafTypesInput.get().getDimension(); i++) {
                leafTypes.add(leafTypesInput.get().getValue(i));
                leafNames.add(String.valueOf(i));
            }
        } else {
            // Fill leaf colour array:
            if (hasTypeTrait()) {
                for (int i = 0; i<typeTraitSet.taxaInput.get().asStringList().size(); i++) {
                    leafTypes.add(getTypeList().indexOf(typeTraitSet.getStringValue(i)));
                    leafNames.add(typeTraitSet.taxaInput.get().asStringList().get(i));
                }
            } else {
                throw new IllegalArgumentException("Either leafColours or "
                    + "trait set (with name '" + typeLabel + "') "
                    + "must be provided.");
            }

        }

        // Count unique leaf types:
        int nUniqueLeafTypes = new HashSet<>(leafTypes).size();
        if (nUniqueLeafTypes > migModel.getNTypes())
            throw new IllegalArgumentException("There are " + nUniqueLeafTypes
                    + " unique leaf types but the model only includes "
                    + migModel.getNTypes() + " unique types!");

        nLeaves = leafTypes.size();
        
        // Set leaf times if specified:
        leafTimes = Lists.newArrayList();
        if (timeTraitSet == null) {
            for (int i=0; i<nLeaves; i++)
                leafTimes.add(0.0);
        } else {
            if (timeTraitSet.taxaInput.get().asStringList().size() != nLeaves)
                throw new IllegalArgumentException("Number of time traits "
                        + "doesn't match number of leaf colours supplied.");
            
            for (int i=0; i<nLeaves; i++)
                leafTimes.add(timeTraitSet.getValue(i));
        }
        

        // Construct tree
        this.root = simulateTree();
        this.root.parent = null;
        this.nodeCount = this.root.getNodeCount();
        this.internalNodeCount = this.root.getInternalNodeCount();
        this.leafNodeCount = this.root.getLeafNodeCount();
        initArrays();

        // Write tree to disk if requested
        if (outputFileNameInput.get() != null) {
            try (PrintStream pstream = new PrintStream(outputFileNameInput.get())) {
                pstream.println("#nexus\nbegin trees;");
                pstream.println("tree TREE_1 = " + toString() + ";");
                pstream.println("end;");
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Error opening file '"
                        + outputFileNameInput.get() + "' for writing.");
            }
        }
    }

    /**
     * Generates tree using the specified list of active leaf nodes using the
     * structured coalescent.
     *
     * @return Root node of generated tree.
     */
    private MultiTypeNode simulateTree() {

        // Initialise node creation counter:
        int nextNodeNr = 0;

        // Initialise node lists:
        List<List<MultiTypeNode>> activeNodes = Lists.newArrayList();
        List<List<MultiTypeNode>> inactiveNodes = Lists.newArrayList();
        for (int i = 0; i < migModel.getNTypes(); i++) {
            activeNodes.add(new ArrayList<>());
            inactiveNodes.add(new ArrayList<>());
        }

        // Add nodes to inactive nodes list:
        for (int l = 0; l < nLeaves; l++) {
            MultiTypeNode node = new MultiTypeNode();
            node.setNr(nextNodeNr);
            node.setID(leafNames.get(l));
            inactiveNodes.get(leafTypes.get(l)).add(node);
            node.setHeight(leafTimes.get(l));
            node.setNodeType(leafTypes.get(l));

            nextNodeNr++;
        }
        
        // Sort nodes in inactive nodes lists in order of increasing age:
        for (int i=0; i<migModel.getNTypes(); i++) {
            Collections.sort(inactiveNodes.get(i),
                (MultiTypeNode node1, MultiTypeNode node2) -> {
                    double dt = node1.getHeight()-node2.getHeight();
                    if (dt<0)
                        return -1;
                    if (dt>0)
                        return 1;
                    
                    return 0;
                });
        }

        // Allocate propensity lists:
        List<List<Double>> migrationProp = new ArrayList<>();
        List<Double> coalesceProp = new ArrayList<>();
        for (int i = 0; i < migModel.getNTypes(); i++) {
            coalesceProp.add(0.0);
            migrationProp.add(new ArrayList<>());
            for (int j = 0; j < migModel.getNTypes(); j++)
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
            MultiTypeNode nextNode = null;
            int nextNodeType = -1;
            double nextTime = Double.POSITIVE_INFINITY;
            for (int i=0; i<migModel.getNTypes(); i++) {
                if (inactiveNodes.get(i).isEmpty())
                    continue;
                
                if (inactiveNodes.get(i).get(0).getHeight()<nextTime) {
                    nextNode = inactiveNodes.get(i).get(0);
                    nextTime = nextNode.getHeight();
                    nextNodeType = i;
                }
            }
            if (nextTime < event.time) {
                t = nextTime;
                activeNodes.get(nextNodeType).add(nextNode);
                inactiveNodes.get(nextNodeType).remove(0);
                continue;
            }
            
            // Step 4: Place event on tree.
            nextNodeNr = updateTree(activeNodes, event, nextNodeNr);

            // Step 5: Keep track of time increment.
            t = event.time;
        }

        // Return sole remaining active node as root:
        for (List<MultiTypeNode> nodeList : activeNodes)
            if (!nodeList.isEmpty())
                return nodeList.get(0);

        // Should not fall through.
        throw new RuntimeException("No active nodes remaining end of "
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
            List<Double> coalesceProp, List<List<MultiTypeNode>> activeNodes) {

        double totalProp = 0.0;

        for (int i = 0; i < migrationProp.size(); i++) {

            double N = migModel.getPopSize(i);
            int k = activeNodes.get(i).size();

            coalesceProp.set(i, k * (k - 1) / (2.0 * N));
            totalProp += coalesceProp.get(i);

            for (int j = 0; j < migrationProp.size(); j++) {

                if (j == i)
                    continue;

                double m = migModel.getBackwardRate(i, j);

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
    private int totalNodesRemaining(List<List<MultiTypeNode>> activeNodes) {
        int result = 0;

        for (List<MultiTypeNode> nodeList : activeNodes)
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
            List<Double> coalesceProp, double totalProp, double t) {

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
        throw new RuntimeException("Structured coalescenct event selection error.");

    }

    /**
     * Update tree with result of latest event.
     *
     * @param activeNodes
     * @param event
     * @param nextNodeNr Integer identifier of last node added to tree.
     * @return Updated nextNodeNr.
     */
    private int updateTree(List<List<MultiTypeNode>> activeNodes, SCEvent event,
            int nextNodeNr) {

        if (event instanceof CoalescenceEvent) {

            // Randomly select node pair with chosen colour:
            MultiTypeNode daughter = selectRandomNode(activeNodes.get(event.fromType));
            MultiTypeNode son = selectRandomSibling(
                    activeNodes.get(event.fromType), daughter);

            // Create new parent node with appropriate ID and time:
            MultiTypeNode parent = new MultiTypeNode();
            parent.setNr(nextNodeNr);
            parent.setID(String.valueOf(nextNodeNr));
            parent.setHeight(event.time);
            nextNodeNr++;

            // Connect new parent to children:
            parent.setLeft(daughter);
            parent.setRight(son);
            son.setParent(parent);
            daughter.setParent(parent);

            // Ensure new parent is set to correct colour:
            parent.setNodeType(event.fromType);

            // Update activeNodes:
            activeNodes.get(event.fromType).remove(son);
            int idx = activeNodes.get(event.fromType).indexOf(daughter);
            activeNodes.get(event.fromType).set(idx, parent);

        } else {

            // Randomly select node with chosen colour:
            MultiTypeNode migrator = selectRandomNode(activeNodes.get(event.fromType));

            // Record colour change in change lists:
            migrator.addChange(event.toType, event.time);

            // Update activeNodes:
            activeNodes.get(event.fromType).remove(migrator);
            activeNodes.get(event.toType).add(migrator);

        }

        return nextNodeNr;

    }

    /**
     * Use beast RNG to select random node from list.
     *
     * @param nodeList
     * @return A randomly selected node.
     */
    private MultiTypeNode selectRandomNode(List<MultiTypeNode> nodeList) {
        return nodeList.get(Randomizer.nextInt(nodeList.size()));
    }

    /**
     * Return random node from list, excluding given node.
     *
     * @param nodeList
     * @param node
     * @return Randomly selected node.
     */
    private MultiTypeNode selectRandomSibling(List<MultiTypeNode> nodeList, Node node) {

        int n = Randomizer.nextInt(nodeList.size() - 1);
        int idxToAvoid = nodeList.indexOf(node);
        if (n >= idxToAvoid)
            n++;

        return nodeList.get(n);
    }
    
    @Override
    public void initStateNodes() { }

    @Override
    public void getInitialisedStateNodes(List<StateNode> stateNodeList) {
        stateNodeList.add(this);
    }
    
    /**
     * Generates an ensemble of trees from the structured coalescent for testing
     * coloured tree-space samplers.
     *
     * @param argv
     * @throws java.lang.Exception
     */
    public static void main(String[] argv) throws Exception {
        
        // Set up migration model.
        RealParameter rateMatrix = new RealParameter();
        rateMatrix.initByName(
                "value", "0.05",
                "dimension", "12");
        RealParameter popSizes = new RealParameter();
        popSizes.initByName(
                "value", "7.0",
                "dimension", "4");
        SCMigrationModel migrationModel = new SCMigrationModel();
        migrationModel.initByName(
                "rateMatrix", rateMatrix,
                "popSizes", popSizes);

        // Specify leaf types:
        IntegerParameter leafTypes = new IntegerParameter();
        leafTypes.initByName(
                "value", "0 0 0");

        // Generate ensemble:
        int reps = 1000000;
        double[] heights = new double[reps];
        double[] changes = new double[reps];

        long startTime = System.currentTimeMillis();
        StructuredCoalescentMultiTypeTree sctree;
        sctree = new StructuredCoalescentMultiTypeTree();
        for (int i = 0; i < reps; i++) {

            if (i % 1000 == 0)
                System.out.format("%d reps done\n", i);

            sctree.initByName(
                    "migrationModel", migrationModel,
                    "leafTypes", leafTypes,
                    "nTypes", 4);

            heights[i] = sctree.getRoot().getHeight();
            changes[i] = sctree.getTotalNumberOfChanges();
        }

        long time = System.currentTimeMillis() - startTime;

        System.out.printf("E[T] = %1.4f +/- %1.4f\n",
                DiscreteStatistics.mean(heights), DiscreteStatistics.stdev(heights) / Math.sqrt(reps));
        System.out.printf("V[T] = %1.4f\n", DiscreteStatistics.variance(heights));

        System.out.printf("E[C] = %1.4f +/- %1.4f\n",
                DiscreteStatistics.mean(changes), DiscreteStatistics.stdev(changes) / Math.sqrt(reps));
        System.out.printf("V[C] = %1.4f\n", DiscreteStatistics.variance(changes));

        System.out.printf("Took %1.2f seconds\n", time / 1000.0);

        try (PrintStream outStream = new PrintStream("heights.txt")) {
            outStream.println("h c");
            for (int i = 0; i < reps; i++)
                outStream.format("%g %g\n", heights[i], changes[i]);
        }
    }

}

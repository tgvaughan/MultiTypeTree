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
package multitypetree.distributions;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.inference.parameter.RealParameter;
import beast.base.evolution.tree.Node;
import multitypetree.evolution.tree.MultiTypeNode;
import multitypetree.evolution.tree.MultiTypeTree;
import multitypetree.evolution.tree.MultiTypeTreeFromNewick;
import multitypetree.evolution.tree.SCMigrationModel;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Tim Vaughan
 */
@Description("Likelihood of ColouredTree under structured coalescent.")
public class StructuredCoalescentTreeDensity extends MultiTypeTreeDistribution {

    public Input<SCMigrationModel> migrationModelInput = new Input<>(
            "migrationModel", "Model of migration between demes.",
            Validate.REQUIRED);
    public Input<Boolean> checkValidityInput = new Input<>(
            "checkValidity", "Explicitly check validity of colouring.  "
            +"(Default false.)  "
            +"Useful if operators are in danger of proposing invalid trees.",
            false);
    
    protected SCMigrationModel migrationModel;
    protected MultiTypeTree mtTree;
    protected boolean checkValidity;

    private enum SCEventKind {
        COALESCE, MIGRATE, SAMPLE
    };

    private class SCEvent {
        double time;
        int type, destType;
        SCEventKind kind;
        Node node;
    }
    private List<SCEvent> eventList;
    private List<Integer[]> lineageCountList;

    // Empty constructor as required:
    public StructuredCoalescentTreeDensity() { };

    @Override
    public void initAndValidate() {
        migrationModel = migrationModelInput.get();
        mtTree = mtTreeInput.get();
        checkValidity = checkValidityInput.get();

        eventList = new ArrayList<>();
        lineageCountList = new ArrayList<>();

        // Ensure tree and migration model are compatible
        if (mtTree.hasTypeTrait() && !mtTree.getTypeSet().equals(migrationModel.getTypeSet()))
            throw new IllegalArgumentException("Tree and migration model have incompatible type sets.");
    }

    @Override
    public double calculateLogP() {
        
        // Check validity of tree if required:
        if (checkValidity && !mtTree.isValid())
            return Double.NEGATIVE_INFINITY;

        // Ensure sequence of events is up-to-date:
        updateEventSequence();

        // Start from the tips of the tree, working up.
        logP = 0;

        // Note that the first event is always a sample. We begin at the first
        // _interval_ and the event following that interval.
        for (int eventIdx = 1; eventIdx<eventList.size(); eventIdx++) {

            SCEvent event = eventList.get(eventIdx);
            Integer[] lineageCount = lineageCountList.get(eventIdx);
            double delta_t = event.time-eventList.get(eventIdx-1).time;

            // Interval contribution:
            if (delta_t>0) {
                double lambda = 0.0;
                for (int c = 0; c<lineageCount.length; c++) {
                    int k = lineageCount[c];
                    double Nc = migrationModel.getPopSize(c);
                    lambda += k*(k-1)/(2.0*Nc);

                    for (int cp = 0; cp<lineageCount.length; cp++) {
                        if (cp==c)
                            continue;

                        double m = migrationModel.getBackwardRate(c, cp);
                        lambda += k*m;
                    }
                }
                logP += -delta_t*lambda;
            }

            // Event contribution:
            switch (event.kind) {
                case COALESCE:
                    double N = migrationModel.getPopSize(event.type);
                    logP += Math.log(1.0/N);
                    break;

                case MIGRATE:
                    double m = migrationModel
                            .getBackwardRate(event.type, event.destType);
                    logP += Math.log(m);
                    break;

                case SAMPLE:
                    // Do nothing here: only effect of sampling event is
                    // to change the lineage counts in subsequent intervals.
                    break;
            }
        }

        return logP;
    }

    /**
     * Determines the sequence of migration, coalescence and sampling events
     * which make up the coloured tree.
     */
    protected void updateEventSequence() {

        // Clean up previous list:
        eventList.clear();
        lineageCountList.clear();
        Node rootNode = mtTree.getRoot();

        // Initialise map of active nodes to active change indices:
        Map<Node, Integer> changeIdx = new HashMap<>();
        changeIdx.put(rootNode, -1);

        // Initialise lineage count per colour array:
        Integer[] lineageCount = new Integer[migrationModel.getNTypes()];
        for (int c = 0; c<migrationModel.getNTypes(); c++)
            if (c==((MultiTypeNode)rootNode).getNodeType())
                lineageCount[c] = 1;
            else
                lineageCount[c] = 0;

        // Calculate event sequence:
        while (!changeIdx.isEmpty()) {

            SCEvent nextEvent = new SCEvent();
            nextEvent.time = Double.NEGATIVE_INFINITY;
            nextEvent.node = rootNode; // Initial assignment not significant

            // Determine next event
            for (Node node : changeIdx.keySet())
                if (changeIdx.get(node)<0) {
                    if (node.isLeaf()) {
                        // Next event is a sample
                        if (node.getHeight()>nextEvent.time) {
                            nextEvent.time = node.getHeight();
                            nextEvent.kind = SCEventKind.SAMPLE;
                            nextEvent.type = ((MultiTypeNode)node).getNodeType();
                            nextEvent.node = node;
                        }
                    } else {
                        // Next event is a coalescence
                        if (node.getHeight()>nextEvent.time) {
                            nextEvent.time = node.getHeight();
                            nextEvent.kind = SCEventKind.COALESCE;
                            nextEvent.type = ((MultiTypeNode)node).getNodeType();
                            nextEvent.node = node;
                        }
                    }
                } else {
                    // Next event is a migration
                    double thisChangeTime = ((MultiTypeNode)node).getChangeTime(changeIdx.get(node));
                    if (thisChangeTime>nextEvent.time) {
                        nextEvent.time = thisChangeTime;
                        nextEvent.kind = SCEventKind.MIGRATE;
                        nextEvent.destType = ((MultiTypeNode)node).getChangeType(changeIdx.get(node));
                        if (changeIdx.get(node)>0)
                            nextEvent.type = ((MultiTypeNode)node).getChangeType(changeIdx.get(node)-1);
                        else
                            nextEvent.type = ((MultiTypeNode)node).getNodeType();
                        nextEvent.node = node;
                    }
                }

            // Update active node list (changeIdx) and lineage count appropriately:
            switch (nextEvent.kind) {
                case COALESCE:
                    Node leftChild = nextEvent.node.getLeft();
                    Node rightChild = nextEvent.node.getRight();

                    changeIdx.remove(nextEvent.node);
                    changeIdx.put(leftChild, ((MultiTypeNode)leftChild).getChangeCount()-1);
                    changeIdx.put(rightChild, ((MultiTypeNode)rightChild).getChangeCount()-1);
                    lineageCount[nextEvent.type]++;
                    break;

                case SAMPLE:
                    changeIdx.remove(nextEvent.node);
                    lineageCount[nextEvent.type]--;
                    break;

                case MIGRATE:
                    lineageCount[nextEvent.destType]--;
                    lineageCount[nextEvent.type]++;
                    int oldIdx = changeIdx.get(nextEvent.node);
                    changeIdx.put(nextEvent.node, oldIdx-1);
                    break;
            }

            // Add event to list:
            eventList.add(nextEvent);
            lineageCountList.add(Arrays.copyOf(lineageCount, lineageCount.length));
        }

        // Reverse event and lineage count lists (order them from tips to root):
        eventList = Lists.reverse(eventList);
        lineageCountList = Lists.reverse(lineageCountList);

    }

    @Override
    public boolean requiresRecalculation() {
        return true;
    }

    /**
     * Test likelihood result. Duplicate of JUnit test for debugging purposes.
     *
     * @param argv
     * @throws java.lang.Exception
     */
    public static void main(String[] argv) throws Exception {

        // Assemble test MultiTypeTree:
        String newickStr =
                "(((A[state=1]:0.25)[state=0]:0.25,B[state=0]:0.5)[state=0]:1.5,"
                +"(C[state=0]:1.0,D[state=0]:1.0)[state=0]:1.0)[state=0]:0.0;";

        MultiTypeTreeFromNewick mtTree = new MultiTypeTreeFromNewick();
        mtTree.initByName(
                "newick", newickStr,
                "typeLabel", "state",
                "nTypes", 2);

        // Assemble migration model:
        RealParameter rateMatrix = new RealParameter();
        rateMatrix.initByName("value", "2.0 1.0");
        RealParameter popSizes = new RealParameter();
        popSizes.initByName("value", "5.0 10.0");
        SCMigrationModel migrationModel = new SCMigrationModel();
        migrationModel.initByName(
                "rateMatrix", rateMatrix,
                "popSizes", popSizes);

        // Set up likelihood instance:
        StructuredCoalescentTreeDensity likelihood = new StructuredCoalescentTreeDensity();
        likelihood.initByName(
                "migrationModel", migrationModel,
                "multiTypeTree", mtTree);

        double expResult = -16.52831;  // Calculated by hand
        double result = likelihood.calculateLogP();

        System.out.println("Expected result: "+expResult);
        System.out.println("Actual result: "+result);
        System.out.println("Difference: "+String.valueOf(result-expResult));

    }
}
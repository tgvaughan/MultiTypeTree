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
package beast.evolution.likelihood;

import beast.core.*;
import beast.core.Input.Validate;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.evolution.migrationmodel.MigrationModel;
import beast.evolution.tree.*;
import beast.util.TreeParser;
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
public class StructuredCoalescentLikelihood extends ColouredTreeDistribution {

	public Input<MigrationModel> m_migrationModel = new Input<MigrationModel>(
			"migrationModel", "Model of migration between demes.",
			Validate.REQUIRED);

	protected MigrationModel migrationModel;
	protected ColouredTree cTree;
	protected Tree tree;

	private enum SCEventType { COALESCE, MIGRATE, SAMPLE };
	private class SCEvent {
		double time;
		int colour, destColour; 
		SCEventType type;
		Node node;
	}

	private List<SCEvent> eventList;
	private List<Integer[]> lineageCountList;

	// Empty constructor as required:
	public StructuredCoalescentLikelihood() {};

	@Override
	public void initAndValidate() {
		migrationModel = m_migrationModel.get();
		cTree = m_ctree.get();
		tree = cTree.getUncolouredTree();

		eventList = new ArrayList<SCEvent>();
		lineageCountList = new ArrayList<Integer[]>();
	}

	@Override
	public double calculateLogP() {

		// Ensure sequence of events is up-to-date:
		updateEventSequence();

		// Start from the tips of the tree, working up.
		logP = 0;

		// Note that the first event is always a sample. We begin at the first
		// _interval_ and the event following that interval.
		for (int eventIdx=1; eventIdx<eventList.size(); eventIdx++) {

			SCEvent event = eventList.get(eventIdx);
			Integer[] lineageCount = lineageCountList.get(eventIdx);
			double delta_t = event.time - eventList.get(eventIdx-1).time;

			// Interval contribution:
			if (delta_t>0) {
				double lambda = 0.0;
				for (int c=0; c<lineageCount.length; c++) {
					int k = lineageCount[c];
					double Nc = migrationModel.getPopSize(c);
					lambda += k*(k-1)/(2.0*Nc);

					for (int cp=0; cp<lineageCount.length; cp++) {
						if (cp==c)
							continue;

						double m = migrationModel.getBackwardRate(cp,c);
						lambda += k*m;
					}
				}
				logP += -delta_t*lambda;
			}

			// Event contribution:
			switch(event.type) {
				case COALESCE:
					double N = migrationModel.getPopSize(event.colour);
					logP += Math.log(1.0/N);
					break;

				case MIGRATE:
					double m = migrationModel
							.getBackwardRate(event.destColour, event.colour);
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
	 * Determines the sequence of migration, coalescence and sampling
	 * events which make up the coloured tree.
	 */
	public void updateEventSequence() {

		// Clean up previous list:
		eventList.clear();
		lineageCountList.clear();
		Node rootNode = tree.getRoot();

		// Initialise map of active nodes to active change indices:
		Map<Node,Integer> changeIdx = new HashMap<Node,Integer>();
		changeIdx.put(rootNode, -1);

		// Initialise lineage count per colour array:
		Integer[] lineageCount = new Integer[cTree.getNColours()];
		for (int c=0; c<cTree.getNColours(); c++) {
			if (c == cTree.getNodeColour(rootNode))
				lineageCount[c] = 1;
			else
				lineageCount[c] = 0;
		}

		// Calculate event sequence:
		while(!changeIdx.isEmpty()) {

			SCEvent nextEvent = new SCEvent();
			nextEvent.time = Double.NEGATIVE_INFINITY;
			nextEvent.node = rootNode; // Initial assignment not significant

			// Determine next event
			for (Node node : changeIdx.keySet()) {
				
				if (changeIdx.get(node)<0) {
					if (node.isLeaf()) {
						// Next event is a sample
						if (node.getHeight()>nextEvent.time) {
							nextEvent.time = node.getHeight();
							nextEvent.type = SCEventType.SAMPLE;
							nextEvent.colour = cTree.getNodeColour(node);
							nextEvent.node = node;
						}
					} else {
						// Next event is a coalescence
						if (node.getHeight()>nextEvent.time) {
							nextEvent.time = node.getHeight();
							nextEvent.type = SCEventType.COALESCE;
							nextEvent.colour = cTree.getNodeColour(node);
							nextEvent.node = node;
						}
					}
				} else {
					// Next event is a migration
					double thisChangeTime = cTree.getChangeTime(node,changeIdx.get(node));
					if (thisChangeTime>nextEvent.time) {
						nextEvent.time = thisChangeTime;
						nextEvent.type = SCEventType.MIGRATE;
						nextEvent.destColour = cTree.getChangeColour(node, changeIdx.get(node));
						if (changeIdx.get(node)>0)
							nextEvent.colour = cTree.getChangeColour(node,
									changeIdx.get(node)-1);
						else
							nextEvent.colour = cTree.getNodeColour(node);
						nextEvent.node = node;
					}
				}
			}

			// Update active node list (changeIdx) and lineage count appropriately:
			switch(nextEvent.type) {
				case COALESCE:
					Node leftChild = nextEvent.node.getLeft();
					Node rightChild = nextEvent.node.getRight();

					changeIdx.remove(nextEvent.node);
					changeIdx.put(leftChild, cTree.getChangeCount(leftChild)-1);
					changeIdx.put(rightChild, cTree.getChangeCount(rightChild)-1);
					lineageCount[nextEvent.colour]++;
					break;

				case SAMPLE:
					changeIdx.remove(nextEvent.node);
					lineageCount[nextEvent.colour]--;
					break;

				case MIGRATE:
					lineageCount[nextEvent.destColour]--;
					lineageCount[nextEvent.colour]++;
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
	 * Test likelihood result.  Duplicate of JUnit test for debugging purposes.
	 * @param argv 
	 */
	public static void main (String[] argv) throws Exception {

		// Assemble test ColouredTree:
		TreeParser parser = new TreeParser();
		String newickStr =
                        "(((A[&state=1]:0.25)[&state=0]:0.25,B[&state=0]:0.5)[&state=0]:1.5,"
                        + "(C[&state=0]:1.0,D[&state=0]:1.0)[&state=0]:1.0)[&state=0]:0.0;";
		parser.initByName(
                        "IsLabelledNewick", true,
                        "adjustTipHeights", true,
                        "singlechild", true,
                        "newick", newickStr);
		Tree flatTree = parser;

		ColouredTree ctree = new ColouredTree();
		ctree.initByName(
                        "nColours", 2,
                        "maxBranchColours", 10,
                        "tree", new Tree(),
                        "changeColours", new IntegerParameter("0"),
                        "changeTimes", new RealParameter("0.0"),
                        "changeCounts", new IntegerParameter("0"),
                        "nodeColours", new IntegerParameter("0"));
		ctree.initFromFlatTree(flatTree);

		// Assemble migration model:
		RealParameter rateMatrix = new RealParameter();
		rateMatrix.initByName(
				"minordimension",2,
				"dimension",4,
				"value","0.0 1.0 2.0 0.0");
		RealParameter popSizes = new RealParameter();
		popSizes.initByName(
				"dimension",2,
				"value","5.0 10.0");
		MigrationModel migrationModel = new MigrationModel();
		migrationModel.initByName(
				"rateMatrix", rateMatrix,
				"popSizes", popSizes);

		System.out.println(migrationModel.getPopSize(0));
		System.out.println(migrationModel.getPopSize(1));
		System.out.println(migrationModel.getBackwardRate(0, 0)+" "+migrationModel.getBackwardRate(0,1));
		System.out.println(migrationModel.getBackwardRate(1, 0)+" "+migrationModel.getBackwardRate(1,1));

		// Set up likelihood instance:
		StructuredCoalescentLikelihood likelihood = new StructuredCoalescentLikelihood();
		likelihood.initByName(
				"migrationModel", migrationModel,
				"colouredTree", ctree);

		double expResult = -16.52831;  // Calculated by hand
		double result = likelihood.calculateLogP();

		System.out.println("Expected result: " + expResult);
		System.out.println("Actual result: " + result);
		System.out.println("Difference: " + String.valueOf(result-expResult));

	}
}
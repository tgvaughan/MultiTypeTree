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
import beast.evolution.migrationmodel.MigrationModel;
import beast.evolution.tree.*;
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

	public Input<IntegerParameter> m_leafColours = new Input<IntegerParameter>(
			"leafColours",
			"Colours of leaf nodes.",
			Validate.REQUIRED);


	protected MigrationModel migrationModel;
	protected IntegerParameter leafColours;
	protected ColouredTree ctree;
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

	@Override
	public void initAndValidate() {
		migrationModel = m_migrationModel.get();
		leafColours = m_leafColours.get();
		ctree = m_ctree.get();
		tree = ctree.getUncolouredTree();

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

			// Abort if interval is of zero length
			// (It's usually a bad idea to check for equivalence of floats,
			// but we make an exception here as failure of the test will
			// still yield a correct result, only less efficiently.)
			if (delta_t==0)
				continue;

			// Interval contribution:
			double lambda = 0.0;
			for (int c=0; c<lineageCount.length; c++) {
				int k = lineageCount[c];
				double N = migrationModel.getPopSizes().getArrayValue(c);
				lambda += k*(k-1)/(4.0*N);

				for (int cp=0; cp<lineageCount.length; cp++) {
					if (cp==c)
						continue;

					double m = migrationModel.getRateMatrix().getMatrixValue(cp, c);
					lambda += k*m;
				}
			}
			logP += -delta_t*lambda;

			// Event contribution:
			switch(event.type) {
				case COALESCE:
					double N = migrationModel
							.getPopSizes()
							.getArrayValue(event.colour);
					logP += Math.log(1.0/(2.0*N));
					break;

				case MIGRATE:
					double m = migrationModel
							.getRateMatrix()
							.getMatrixValue(event.colour,event.destColour);
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
		changeIdx.put(rootNode, ctree.getChangeCount(rootNode)-1);

		// Initialise lineage count per colour array:
		Integer[] lineageCount = new Integer[ctree.getNColours()];
		for (int c=0; c<ctree.getNColours(); c++) {
			if (c != ctree.getNodeColour(rootNode))
				lineageCount[c] = 0;
			else
				lineageCount[c] = 1;
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
							nextEvent.colour = ctree.getNodeColour(node);
							nextEvent.node = node;
						}
					} else {
						// Next event is a coalescence
						if (node.getHeight()>nextEvent.time) {
							nextEvent.time = node.getHeight();
							nextEvent.type = SCEventType.COALESCE;
							nextEvent.colour = ctree.getNodeColour(node);
							nextEvent.node = node;
						}
					}
				} else {
					// Next event is a migration
					double thisChangeTime = ctree.getChangeTime(node,changeIdx.get(node));
					if (thisChangeTime>nextEvent.time) {
						nextEvent.time = thisChangeTime;
						nextEvent.type = SCEventType.MIGRATE;
						nextEvent.colour = ctree.getChangeColour(node, changeIdx.get(node));
						if (changeIdx.get(node)>0)
							nextEvent.destColour = ctree.getChangeColour(node,
									changeIdx.get(node)-1);
						else
							nextEvent.destColour = ctree.getNodeColour(node);
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
					changeIdx.put(leftChild, ctree.getChangeCount(leftChild)-1);
					changeIdx.put(rightChild, ctree.getChangeCount(rightChild)-1);
					lineageCount[nextEvent.colour]++;
					break;

				case SAMPLE:
					changeIdx.remove(nextEvent.node);
					lineageCount[nextEvent.colour]--;
					break;

				case MIGRATE:
					lineageCount[nextEvent.colour]--;
					lineageCount[nextEvent.destColour]++;
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
}
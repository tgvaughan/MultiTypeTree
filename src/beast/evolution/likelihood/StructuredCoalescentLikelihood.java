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
import java.util.ArrayList;
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
	}

	private List<SCEvent> eventList;
	private List<List<Integer>> lineageCountList;

	@Override
	public void initAndValidate() {
		migrationModel = m_migrationModel.get();
		leafColours = m_leafColours.get();
		ctree = m_ctree.get();
		tree = ctree.getUncolouredTree();
	}

	@Override
	public double calculateLogP() {

		// Ensure sequence of events is up-to-date:
		updateEventSequence();

		// Start from the tips of the tree, working up.

		double likelihood = 0;
		return likelihood;
	}

	/**
	 * Determines the sequence of migration, coalescence and sampling
	 * events which make up the coloured tree.
	 */
	public void updateEventSequence() {

		List<Node> nodeList = new ArrayList<Node>();
		nodeList.add(tree.getRoot());

		// Record root coalescence event to get the ball rolling
		SCEvent event = new SCEvent();
		event.type = SCEventType.COALESCE;
		event.time = tree.getRoot().getHeight();
		event.colour = ctree.getNodeColour(tree.getRoot());
		eventList.add(event);

		while(nodeList.size()>0) {

			// Initialise changeIdx:
			Map<Node,Integer> changeIdx = new HashMap<Node,Integer>();
			for (Node node : nodeList)
				changeIdx.put(node, ctree.getChangeCount(node)-1);

			SCEvent nextEvent = new SCEvent();
			nextEvent.time = Double.NEGATIVE_INFINITY;
			Node eventNode = nodeList.get(0);

			// Determine next event
			for (Node node : nodeList) {
				
				if (changeIdx.get(node)<0) {
					if (node.isLeaf()) {
						// Next event is a sample
						if (node.getHeight()>nextEvent.time) {
							nextEvent.time = node.getHeight();
							nextEvent.type = SCEventType.SAMPLE;
							nextEvent.colour = ctree.getNodeColour(node);
							eventNode = node;
						}
					} else {
						// Next event is a coalescence
						if (node.getHeight()>nextEvent.time) {
							nextEvent.time = node.getHeight();
							nextEvent.type = SCEventType.COALESCE;
							nextEvent.colour = ctree.getNodeColour(node);
							eventNode = node;
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
						eventNode = node;
					}
				}
			}

			// Add event to list:
			eventList.add(nextEvent);

			// Update state appropriately:
			switch(nextEvent.type) {
				case COALESCE:
					break;
				case SAMPLE:
					break;
				case MIGRATE:
					break;
			}
		}

	}

	@Override
	public boolean requiresRecalculation() {
		return true;
	}
}
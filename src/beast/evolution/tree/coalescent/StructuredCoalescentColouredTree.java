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
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.evolution.tree.ColouredTree;
import beast.util.Randomizer;
import java.util.ArrayList;
import java.util.List;

/**
 * A coloured tree generated randomly from leaf colours and a migration
 * matrix with fixed population sizes.
 *
 * @author Tim Vaughan
 */
@Description("A coloured tree generated randomly from leaf colours and"
		+ "a migration matrix with fixed population sizes.")
public class StructuredCoalescentColouredTree extends ColouredTree {

	/*
	 * Plugin inputs:
	 */

	public Input<RealParameter> rateMatrixInput = new Input<RealParameter>(
			"rateMatrix",
			"Migration rate matrix (diagonals are ignored)",
			Validate.REQUIRED);

	public Input<RealParameter> popSizesInput = new Input<RealParameter>(
			"popSizes",
			"Deme population sizes.",
			Validate.REQUIRED);

	public Input<IntegerParameter> leafColoursInput = new Input<IntegerParameter>(
			"leafColours",
			"Colours of leaf nodes.");

	/*
	 * Shadowing fields:
	 */

	protected RealParameter rateMatrix;
	protected RealParameter popSizes;
	protected IntegerParameter leafColours;

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
		@Override
		boolean isCoalescence() {
			return false;
		}
	}
	
	public StructuredCoalescentColouredTree() {}

	@Override
	public void initAndValidate() throws Exception {

		// Obtain required parameters from inputs:
		nColours = nColoursInput.get();
		colourLabel = colourLabelInput.get();
		maxBranchColours = maxBranchColoursInput.get();
		rateMatrix = rateMatrixInput.get();
		popSizes = popSizesInput.get();

		// Obtain leaf colours:
		leafColours = leafColoursInput.get();

		// Allocate parameters used to store colouring:
		changeColours = new IntegerParameter("0");
		changeTimes = new RealParameter("0.0");
		changeCounts = new IntegerParameter("0");
		nodeColours = new IntegerParameter("0");

		// Attach state nodes to temporary State, as Parameters must
		// belong to a State before they can be modified by calls to
		// setValue(). (Why?)
		State state = new State();
		state.initByName(
				"stateNode", changeColours,
				"stateNode", changeTimes,
				"stateNode", changeCounts,
				"stateNode", nodeColours);
		state.initialise();

		// Ensure inputs retain references to colouring parameters:
		changeColoursInput.setValue(changeColours, this);
		changeTimesInput.setValue(changeTimes, this);
		changeCountsInput.setValue(changeCounts, this);

		// Allocate arrays for recording colour change information:
		int nBranches = 2*leafColours.getDimension() - 2;
		changeColours.setDimension(nBranches*maxBranchColours);
		changeTimes.setDimension(nBranches*maxBranchColours);
		changeCounts.setDimension(nBranches);
		nodeColours.setDimension(nBranches+1);

		// Construct tree:
		tree = new Tree(simulateTree());

		// Ensure colouring is internally consistent:
		if (!isValid())
			throw new Exception("Inconsistent colour assignment.");

		// Assign tree to input plugin:
		treeInput.setValue(tree, this);
	}

	/**
	 * Generates tree using the specified list of active leaf nodes
	 * using the structured coalescent.
	 * 
	 * @param activeNodes
	 * @return Root node of generated tree.
	 */
	private Node simulateTree() throws Exception {

		// Initialise node creation counter:
		int nextNodeNr = 0;

		// Initialise active node list:
		List<List<Node>> activeNodes = new ArrayList<List<Node>>();
		for (int i=0; i<nColours; i++)
			activeNodes.add(new ArrayList<Node>());

		for (int l=0; l<leafColours.getDimension(); l++) {
			Node node = new Node();
			node.setHeight(0.0);
			node.setNr(nextNodeNr);
			node.setID(String.valueOf(nextNodeNr));
			activeNodes.get(leafColours.getValue(l)).add(node);
			setNodeColour(node, leafColours.getValue(l));

			nextNodeNr++;
		}

		// Allocate propensity lists:
		List<List<Double>> migrationProp = new ArrayList<List<Double>>();
		List<Double> coalesceProp = new ArrayList<Double>();
		for (int i=0; i<rateMatrix.getMinorDimension1(); i++) {
			coalesceProp.add(0.0);
			migrationProp.add(new ArrayList<Double>());
			for (int j=0; j<rateMatrix.getMinorDimension1(); j++)
				migrationProp.get(i).add(0.0);
		}

		double t = 0.0;

		while (totalNodesRemaining(activeNodes)>1) {

			// Step 1: Calculate propensities.
			double totalProp = updatePropensities(migrationProp, coalesceProp,
					activeNodes);

			// Step 2: Determine next event.
			SCEvent event = getNextEvent(migrationProp, coalesceProp,
					totalProp, t); 

			// Step 3: Place event on tree.
			nextNodeNr = updateTree(activeNodes, event, nextNodeNr);

			// Step 4: Keep track of time increment.
			t = event.time;
		}

		// Return first remaining active node as root:
		for (List<Node> nodeList : activeNodes) {
			if (!nodeList.isEmpty())
				return nodeList.get(0);
		}

		// Should not fall through.
		throw new Exception("No active nodes remaining end of"
				+ "structured coalescent simulation!");
	}

	/**
	 * Obtain propensities (instantaneous reaction rates) for coalescence
	 * and migration events.
	 * 
	 * @param migrationProp
	 * @param coalesceProp
	 * @param activeNodes
	 * @return Total reaction propensity.
	 */
	private double updatePropensities(List<List<Double>> migrationProp,
			List<Double> coalesceProp, List<List<Node>> activeNodes) {

		double totalProp = 0.0;

		for (int i=0; i<migrationProp.size(); i++) {

			double N = popSizes.getArrayValue(i);
			int k = activeNodes.get(i).size();

			coalesceProp.set(i, k*(k-1)/(4.0*N));
			totalProp += coalesceProp.get(i);

			for (int j=0; j<migrationProp.size(); j++) {

				if (j==i)
					continue;

				double m = rateMatrix.getMatrixValue(i, j);

				migrationProp.get(i).set(j, k*m);
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
		t += Randomizer.nextExponential(1.0/totalProp);

		// Select event type:
		double U = Randomizer.nextDouble()*totalProp;
		for (int i=0; i<migrateProp.size(); i++) {

			if (U<coalesceProp.get(i))
				return new CoalescenceEvent(i, t);
			else
				U -= coalesceProp.get(i);

			for (int j=0; j<migrateProp.size(); j++) {

				if (j==i)
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
	private int updateTree (List<List<Node>> activeNodes, SCEvent event,
			int nextNodeNr) {

		if (event.isCoalescence()) {

			// Randomly select node pair with chosen colour:
			Node daughter = selectRandomNode(activeNodes.get(event.fromColour));
			Node son = selectRandomSibling(
					activeNodes.get(event.fromColour), daughter);

			// Create new parent node with appropriate ID and time:
			Node parent = new Node();
			parent.setHeight(event.time);
			parent.setNr(nextNodeNr);
			parent.setID(String.valueOf(nextNodeNr));
			nextNodeNr++;

			// Connect new parent to children:
			parent.setLeft(daughter);
			parent.setRight(son);
			son.setParent(parent);
			daughter.setParent(parent);

			// Ensure new parent is set to correct colour:
			setNodeColour(parent, event.fromColour);

			// Update activeNodes:
			activeNodes.get(event.fromColour).remove(son);
			int idx = activeNodes.get(event.fromColour).indexOf(daughter);
			activeNodes.get(event.fromColour).set(idx, parent);

		} else {

			// Randomly select node with chosen colour:
			Node migrator = selectRandomNode(activeNodes.get(event.fromColour));

			// Record colour change in change lists:
			addChange(migrator, event.toColour, event.time);

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

		int n = Randomizer.nextInt(nodeList.size()-1);
		int idxToAvoid = nodeList.indexOf(node);
		if (n>=idxToAvoid)
			n++;

		return nodeList.get(n);
	}
	
}

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
import beast.core.parameter.RealParameter;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeColour;
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
public class StructuredCoalescentTreeColour extends TreeColour {

	/*
	 * Plugin inputs:
	 */

	public Input<RealParameter> rateMatrixInput = new Input<RealParameter>(
			"rateMatrix",
			"Migration rate matrix with diagonals representing deme"
			+ "population sizes.", Validate.REQUIRED);

	/*
	 * Shadowing fields:
	 */

	protected RealParameter rateMatrix;

	/*
	 * Other private fields and classes:
	 */

	private abstract class SCEvent {
		double time;
		int fromColour, toColour; 

		boolean isMigration() {
			return false;
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
		boolean isMigration() {
			return true;
		}
	}
	
	public StructuredCoalescentTreeColour() {}

	@Override
	public void initAndValidate() {

		// Obtain required parameters from inputs:
		nColours = nColoursInput.get();
		colourLabel = colourLabelInput.get();
		maxBranchColours = maxBranchColoursInput.get();
		rateMatrix = rateMatrixInput.get();

		// Create new tree:
		tree = new Tree();

		// Obtain leaf colours:
		leafColours = leafColoursInput.get();

		// Obtain references to Parameters used to store colouring:
		changeColours = changeColoursInput.get();
		changeTimes = changeTimesInput.get();
		changeCounts = changeCountsInput.get();
		
		// Initialise active node and node colour lists:
		List<List<Node>> activeNodes = new ArrayList<List<Node>>();
		for (int i=0; i<nColours; i++)
			activeNodes.add(new ArrayList<Node>());

		for (int l=0; l<leafColours.getDimension(); l++) {
			Node node = new Node();
			node.setHeight(0.0);
			activeNodes.get(leafColours.getValue(l)).add(node);
		}

		// Allocate arrays for recording colour change information:
		int nBranches = 2*leafColours.getDimension() - 2;
		changeColours.setDimension(nBranches*maxBranchColours);
		changeTimes.setDimension(nBranches*maxBranchColours);
		changeCounts.setDimension(nBranches);

	}

	/**
	 * Generates tree using the specified list of active leaf nodes
	 * using the structured coalescent.
	 * 
	 * @param activeNodes
	 * @return Root node of generated tree.
	 */
	private Node simulateTree(List<List<Node>> activeNodes) throws Exception {

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

			double N = rateMatrix.getMatrixValue(i, i);
			int k = activeNodes.get(i).size();

			coalesceProp.set(i, k*(k-1)/(4.0*N));
			totalProp += coalesceProp.get(i);

			for (int j=0; j<migrationProp.size(); j++) {

				if (j==i)
					continue;

				migrationProp.get(i).set(j, k*rateMatrix.getMatrixValue(i, j));
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


	
}
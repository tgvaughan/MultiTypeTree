/*
 * Copyright (C) 2012 Tim Vaughan <tgvaughan@gmail.com>
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
package beast.evolution.operators;

import beast.core.Description;
import beast.core.Input;
import beast.evolution.tree.Node;
import beast.math.GammaFunction;
import beast.util.PoissonRandomizer;
import beast.util.Randomizer;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A subtree slide operator for coloured trees.
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("A subtree slide operator for coloured trees. ")
public class ColouredSubtreeSlide extends ColouredTreeOperator {

	public Input<Double> muInput = new Input<Double>("mu",
			"Mean rate of colour change along branch.", Input.Validate.REQUIRED);

	public Input<Double> rootSlideParamInput = new Input<Double>("rootSlideParam",
			"Root sliding is restricted to [sister.height,root.height+rootSlideParam]",
			Input.Validate.REQUIRED);

	private double mu, rootSlideParam;

	@Override
	public void initAndValidate() { }

	@Override
	public double proposal() {

		cTree = colouredTreeInput.get();
		tree = cTree.getUncolouredTree();
		mu = muInput.get();
		rootSlideParam = rootSlideParamInput.get();

		// Keep track of Hastings ratio while generating proposal:
		double logHR = 0;

		// Choose non-root node:
		Node node;
		do {
			node = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
		} while (node.isRoot());

		Node parent = node.getParent();
		Node sister = getOtherChild(parent, node);

		double t_node = node.getHeight();
		double t_parentOld = parent.getHeight();
		double t_sister = sister.getHeight();

		if (parent.isRoot()) {
			
			// Select new height of parent:
			double u = Randomizer.nextDouble();
			double f = u*rootSlideParam + (1.0-u)/rootSlideParam;
			double tMin = Math.max(t_node, t_sister);
			double t_parentNew = tMin + f*(t_parentOld-tMin);

			// Record old number of colour change events:
			int nChangesNodeOld = cTree.getChangeCount(node);
			int nChangesSisterOld = cTree.getChangeCount(sister);

			// Select number of colour change events:
			int nChangesNode = PoissonRandomizer.nextInt(mu*(t_parentNew-t_node));
			int nChangesSister = PoissonRandomizer.nextInt(mu*(t_parentNew-t_sister));

			// Record forward move probability:
			logHR -= getLogRootMoveProb(node, t_parentNew,
					nChangesNode, nChangesSister);

			// Assign new height to parent:
			parent.setHeight(t_parentNew);

			// Recolour branch between node, up to root, then down toward
			// sister to a height of tOld.  Reject outright if final colour
			// at the end of this path on sister is different to the original
			// colour at that point.
			if (!recolourRoot(node, nChangesNode, nChangesSister))
				return Double.NEGATIVE_INFINITY;

			// Record reverse move probability:
			logHR += getLogRootMoveProb(node, t_parentOld,
					nChangesNodeOld, nChangesSisterOld);

		} else {

			// TODO: Add standard subtree slide

		}

		return logHR;
	}
	
	/**
	 * Obtain probability of a particular move of branch starting at node
	 * to join to its parent at time newParentTime and with nChanges colour
	 * changes along it.  Also handles moves in which the root node is
	 * repositioned.
	 * 
	 * @param node
	 * @param newParentTime
	 * @param nChanges
	 * @return Log of move probability.
	 */
	double getLogRootMoveProb(Node node, double newParentTime,
			int nChangesNode, int nChangesSister) {
		double logP = 0;
		
		Node parent = node.getParent();
		Node sister = getOtherChild(parent, node);
		
		double t_node = node.getHeight();
		double t_parentOld = parent.getHeight();
		double t_sister = sister.getHeight();
		
		double tMinHard = Math.max(t_node,t_sister);
		double Told = t_parentOld - tMinHard;
		double Tnew = newParentTime - tMinHard;
		
		// Reject outright if proposed time outside of bounds:
		if (Tnew/Told<1.0/rootSlideParam || Tnew/Told>rootSlideParam)
			return Double.NEGATIVE_INFINITY;
		
		// Probability of selecting new time:
		logP += Math.log(1.0/Told);
		
		// Probability of selecting nChanges colour change events:
		double Lnode = newParentTime - t_node;
		double Lsister = newParentTime - t_sister;
		logP += -Lnode*mu + nChangesNode*Math.log(Lnode*mu)
				- GammaFunction.lnGamma(nChangesNode+1);
		logP += -Lsister*mu + nChangesSister*Math.log(Lsister*mu)
				- GammaFunction.lnGamma(nChangesSister+1);

		
		// Probability of selecting particular colour change times:
		logP += GammaFunction.lnGamma(nChangesNode+1)
				- nChangesNode*Math.log(Lnode);
		logP += GammaFunction.lnGamma(nChangesSister+1)
				- nChangesSister*Math.log(Lsister);
		
		// Probability of selecting a particular colour assignment:
		logP += (nChangesNode+nChangesSister)*Math.log(1.0/(cTree.getNColours()-1));
				
		return logP;
	}

	/**
	 * Recolour branch above node with nChanges random colour changes.
	 * 
	 * @param node
	 * @param nChanges 
	 * @return True if selected colour change consistent.
	 */
	private boolean recolourRoot(Node node,
			int nChangesNode, int nChangesSister) {

		// Early exit for single-coloured trees:
		if (cTree.getNColours()<2)
			return true;
		
		Node parent = node.getParent();
		Node sister = getOtherChild(parent, node);
		
		double t_node = node.getHeight();
		double t_parent = parent.getHeight();
		double t_sister = sister.getHeight();
		
		int nodeCol = cTree.getNodeColour(node);
		int sisterCol = cTree.getNodeColour(sister);
		
		// Clear current changes:
		setChangeCount(node, 0);
		setChangeCount(sister, 0);
		
		// Assign changes on branch above node:
		if (nChangesNode != 0) {
			double[] times = getTimes(t_parent-t_node, nChangesNode);
			int[] colours = getColours(nodeCol, nChangesNode);
			for (int i=0; i<nChangesNode; i++) {
				addChange(node, colours[i], times[i] + t_node);
			}
			setNodeColour(parent, colours[nChangesNode-1]);
			
		} else {
			setNodeColour(parent, nodeCol);
		}
		
		int parentCol = cTree.getNodeColour(parent);
		
		// Assign changes on branch above sister:
		if (nChangesSister != 0) {
			double [] times = getTimes(t_parent-t_sister, nChangesSister);
			int [] colours = getColours(sisterCol, nChangesSister);

			// Force reject if colouring inconsistent:
			if (colours[nChangesSister-1]!=parentCol)
				return false;
			
			for (int i=0; i<nChangesSister; i++)
				addChange(sister, colours[i], times[i] + t_sister);
			
		} else {
			// Force reject if colouring inconsistent:
			if (parentCol != sisterCol)
				return false;
		}

		return true;
	}
	
	/**
     * Randomly select colour change times uniformly along branch.
     *
     * @param initialTime
     * @param finalTime
     * @return Array of colour change times.
     */
    private double[] getTimes(double L, int nChanges) {

        // Assign random times between initialTime and finalTime:
        double[] times = new double[nChanges];
        for (int i=0; i<nChanges; i++)
            times[i] = L*Randomizer.nextDouble();
        Arrays.sort(times);

        return times;
    }

    /**
     * Randomly assign nChanges colour changes to branch.
     *
     * @param nChanges
     * @return Array of colours.
     */
    private int[] getColours(int startColour, int nChanges) {

        int[] colours = new int[nChanges];
        int nColours = cTree.getNColours();

		int lastCol = startColour;
        for (int i=0; i<nChanges; i++) {
			int newCol;
			do {
	            newCol = Randomizer.nextInt(nColours);
			} while (newCol == lastCol);
            colours[i] = newCol;
			lastCol = newCol;
        }

        return colours;
    }
	
}

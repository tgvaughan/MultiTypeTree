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
import beast.util.Randomizer;

/**
 * A subtree slide operator for coloured trees.  This version uses an
 * unconditioned random walk amongst demes to recolour altered branches.
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("A subtree slide operator for coloured trees. This version uses"
        + " an unconditioned random walk amongst demes to recolour altered"
        + " branches.")
public class ColouredSubtreeSlideRandom extends RandomRetypeOperator {

    public Input<Double> rootSlideParamInput = new Input<Double>("rootSlideParam",
            "Root sliding is restricted to [sister.height,root.height+rootSlideParam]",
            Input.Validate.REQUIRED);
    private double rootSlideParam;

    @Override
    public void initAndValidate() { }

    @Override
    public double proposal() {

        mtTree = multiTypeTreeInput.get();
        tree = mtTree.getUncolouredTree();
        rootSlideParam = rootSlideParamInput.get();

        // Keep track of Hastings ratio while generating proposal:

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
            
            // Record probability of current root branch colouring:
            double logHR = getRootBranchTypeProb(node);

            // Select new height of parent:
            double u = Randomizer.nextDouble();
            double f = u*rootSlideParam+(1.0-u)/rootSlideParam;
            double tMin = Math.max(t_node, t_sister);
            double t_parentNew = tMin+f*(t_parentOld-tMin);

            // Assign new height to parent:
            parent.setHeight(t_parentNew);
            
            // Recolour branches between node, the root and node's sister:
            try {
                logHR -= retypeRootBranches(node);
            } catch (RecolouringException ex) {
                if (mtTree.discardWhenMaxExceeded()) {
                    ex.discardMsg();
                    return Double.NEGATIVE_INFINITY;
                } else
                    ex.throwRuntime();
            }
            
            // Reject outright if colouring inconsistent:
            if (mtTree.getNodeColour(parent) != mtTree.getFinalBranchColour(sister))
                return Double.NEGATIVE_INFINITY;

            // Incorporate node height change into HR:
            logHR += Math.log((t_parentOld-tMin)/(t_parentNew-tMin));
            
            return logHR;

        } else {
            // TODO: Add standard subtree slide
            double logHR = 0.0;
            
            return logHR;
        }
    }
}

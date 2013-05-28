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

import beast.core.Input;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.Node;
import beast.util.Randomizer;

/**
 * Abstract class of operators on MultiTypeTrees which retype branches using a
 * fixed-rate continuous time random walk across demes.
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public abstract class RandomRetypeOperator extends MultiTypeTreeOperator {

    public Input<Double> muInput = new Input<Double>("mu",
            "Migration rate for proposal distribution", Input.Validate.REQUIRED);

    /**
     * Retype branch between srcNode and its parent with rate fixed by the
     * tuning parameter mu.
     *
     * @param srcNode
     * @return Probability of branch typing
     */
    protected double retypeBranch(Node srcNode) {
        
        double mu = muInput.get();

        Node srcNodeParent = srcNode.getParent();
        double t_srcNode = srcNode.getHeight();
        double t_srcNodeParent = srcNodeParent.getHeight();

        int srcNodeType = ((MultiTypeNode)srcNode).getNodeType();

        // Clear existing changes in preparation for adding replacements:
        ((MultiTypeNode)srcNode).clearChanges();

        double t = t_srcNode;
        int lastType = srcNodeType;
        while (t < t_srcNodeParent) {

            // Determine time to next migration event:
            t += Randomizer.nextExponential(mu);

            if (t < t_srcNodeParent) {

                // Select new colour:
                int newType = Randomizer.nextInt(mtTree.getNTypes() - 1);
                if (newType >= lastType)
                    newType += 1;
                ((MultiTypeNode)srcNode).addChange(newType, t);

                lastType = newType;
            }
        }

        // Return log of branch type probability:
        return -mu*(t_srcNodeParent - t_srcNode)
                + ((MultiTypeNode)srcNode).getChangeCount()*Math.log(mu/(mtTree.getNTypes()-1));

    }

    /**
     * Get probability of the colouring along the branch between srcNode
     * and its parent.
     * 
     * @param srcNode
     * @return Probability of the colouring.
     */
    protected double getBranchTypeProb(Node srcNode) {

        double mu = muInput.get();
        double T = srcNode.getParent().getHeight()
                - srcNode.getHeight();
        int n = ((MultiTypeNode)srcNode).getChangeCount();
        int N = mtTree.getNTypes();

        if (N == 0)
            return 0.0;
        else
            return -mu*T + n*Math.log(mu/(N-1));
    }

}

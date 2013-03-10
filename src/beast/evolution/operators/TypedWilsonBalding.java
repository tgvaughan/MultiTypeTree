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
package beast.evolution.operators;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.evolution.tree.MultiTypeNode;
import beast.util.Randomizer;

/**
 * Wilson-Balding branch swapping operator applied to coloured trees.
 *
 * @author Tim Vaughan
 */
@Description("Implements the unweighted Wilson-Balding branch"
+" swapping move.  This move is similar to one proposed by WILSON"
+" and BALDING 1998 and involves removing a subtree and"
+" re-attaching it on a new parent branch. "
+" See <a href='http://www.genetics.org/cgi/content/full/161/3/1307/F1'>picture</a>."
+" This version retypes each newly generated branch by drawing a"
+" path from the migration model conditional on the types at the"
+" branch ends.")
public class TypedWilsonBalding extends UniformizationRetypeOperator {

    public Input<Double> alphaInput = new Input<Double>("alpha",
            "Root height proposal parameter", Validate.REQUIRED);
    private double alpha;

    @Override
    public void initAndValidate() {
    }

    @Override
    public double proposal() {
        mtTree = multiTypeTreeInput.get();
        alpha = alphaInput.get();

        // Check that operator can be applied to tree:
        if (mtTree.getLeafNodeCount()<3)
            throw new IllegalStateException("Tree too small for"
                    +" TypedWilsonBalding operator.");

        // Select source node:
        MultiTypeNode srcNode;
        do {
            srcNode = mtTree.getNode(Randomizer.nextInt(mtTree.getNodeCount()));
        } while (invalidSrcNode(srcNode));
        MultiTypeNode srcNodeP = srcNode.getParent();
        MultiTypeNode srcNodeS = getOtherChild(srcNodeP, srcNode);
        double t_srcNode = srcNode.getHeight();
        double t_srcNodeP = srcNodeP.getHeight();
        double t_srcNodeS = srcNodeS.getHeight();

        // Select destination branch node:
        MultiTypeNode destNode;
        do {
            destNode = mtTree.getNode(Randomizer.nextInt(mtTree.getNodeCount()));
        } while (invalidDestNode(srcNode, destNode));
        MultiTypeNode destNodeP = destNode.getParent();
        double t_destNode = destNode.getHeight();

        // Handle special cases involving root:

        if (destNode.isRoot()) {
            // FORWARD ROOT MOVE

            double logHR = 0.0;

            // Record probability of current colouring:
            logHR += getBranchColourProb(srcNode);

            // Record srcNode grandmother height:
            double t_srcNodeG = srcNodeP.getParent().getHeight();

            // Choose new root height:
            double newTime = t_destNode+Randomizer.nextExponential(1.0/(alpha*t_destNode));

            // Implement tree changes:
            disconnectBranch(srcNode);
            connectBranchToRoot(srcNode, destNode, newTime);
            mtTree.setRoot(srcNodeP);

            // Recolour root branches:
            logHR -= recolourRootBranches(srcNode);

            // Return HR:
            logHR += Math.log(alpha*t_destNode)
                    +(1.0/alpha)*(newTime/t_destNode-1.0)
                    -Math.log(t_srcNodeG-Math.max(t_srcNode, t_srcNodeS));

            return logHR;
        }

        if (srcNodeP.isRoot()) {
            // BACKWARD ROOT MOVE
            
            double logHR = 0.0;

            // Incorporate probability of current colouring:
            logHR += getRootBranchColourProb(srcNode);

            // Record old srcNode parent height
            double oldTime = t_srcNodeP;

            // Choose height of new attachement point:
            double min_newTime = Math.max(t_srcNode, t_destNode);
            double t_destNodeP = destNodeP.getHeight();
            double span = t_destNodeP-min_newTime;
            double newTime = min_newTime+span*Randomizer.nextDouble();

            // Implement tree changes:
            disconnectBranchFromRoot(srcNode);
            connectBranch(srcNode, destNode, newTime);
            srcNodeS.setParent(null);
            mtTree.setRoot(srcNodeS);

            // Recolour new branch:
            logHR -= recolourBranch(srcNode);

            // Return HR:
            logHR += Math.log(t_destNodeP-Math.max(t_srcNode, t_destNode))
                    -Math.log(alpha*t_srcNodeS)
                    -(1.0/alpha)*(oldTime/t_srcNodeS-1.0);

            return logHR;
        }
        
        // NON-ROOT MOVE

        double logHR = 0.0;

        // Incorporate probability of current colouring.
        logHR += getBranchColourProb(srcNode);

        // Record srcNode grandmother height:
        double t_srcNodeG = srcNodeP.getParent().getHeight();

        // Choose height of new attachment point:
        double min_newTime = Math.max(t_destNode, t_srcNode);
        double t_destNodeP = destNodeP.getHeight();
        double span = t_destNodeP-min_newTime;
        double newTime = min_newTime+span*Randomizer.nextDouble();

        // Implement tree changes:
        disconnectBranch(srcNode);
        connectBranch(srcNode, destNode, newTime);

        // Recolour new branch:
        logHR -= recolourBranch(srcNode);

        // HR contribution of topology and node height changes:
        logHR += Math.log(t_destNodeP-Math.max(t_srcNode, t_destNode))
                -Math.log(t_srcNodeG-Math.max(t_srcNode, t_srcNodeS));

        return logHR;
    }

    /**
     * Returns true if srcNode CANNOT be used for the CWBR move.
     *
     * @param srcNode
     * @return True if srcNode invalid.
     */
    private boolean invalidSrcNode(MultiTypeNode srcNode) {

        if (srcNode.isRoot())
            return true;

        MultiTypeNode parent = srcNode.getParent();

        // This check is important in avoiding situations where it is
        // impossible to choose a valid destNode:
        if (parent.isRoot()) {

            MultiTypeNode sister = getOtherChild(parent, srcNode);

            if (sister.isLeaf())
                return true;

            if (srcNode.getHeight()>=sister.getHeight())
                return true;
        }

        return false;
    }

    /**
     * Returns true if destNode CANNOT be used for the CWBR move in conjunction
     * with srcNode.
     *
     * @param srcNode
     * @param destNode
     * @return True if destNode invalid.
     */
    private boolean invalidDestNode(MultiTypeNode srcNode, MultiTypeNode destNode) {

        if (destNode==srcNode
                ||destNode==srcNode.getParent()
                ||destNode.getParent()==srcNode.getParent())
            return true;

        MultiTypeNode srcNodeP = srcNode.getParent();
        MultiTypeNode destNodeP = destNode.getParent();

        if (destNodeP!=null&&(destNodeP.getHeight()<=srcNode.getHeight()))
            return true;

        return false;
    }
}
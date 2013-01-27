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
import beast.evolution.tree.Node;
import beast.util.Randomizer;

/**
 * @author Tim Vaughan
 */
@Description("Implements the unweighted Wilson-Balding branch"
+"swapping move.  This move is similar to one proposed by WILSON"
+"and BALDING 1998 and involves removing a subtree and"
+"re-attaching it on a new parent branch. "
+"See <a href='http://www.genetics.org/cgi/content/full/161/3/1307/F1'>picture</a>."
        + " This version performs no explicit recolouring.  Used for testing "
        + " Ewing et al.'s sampler.")
public class ColouredWilsonBaldingEasy extends ColouredTreeOperator {

    public Input<Double> alphaInput = new Input<Double>("alpha",
            "Root height proposal parameter", .1);
    private double alpha;

    @Override
    public void initAndValidate() {
    }

    @Override
    public double proposal() {
        cTree = colouredTreeInput.get();
        tree = cTree.getUncolouredTree();
        alpha = alphaInput.get();

        // Check that operator can be applied to tree:
        if (tree.getLeafNodeCount()<3)
            throw new IllegalStateException("Tree too small for"
                    +" ColouredWilsonBaldingRandom operator.");

        // Select source node:
        Node srcNode;
        do {
            srcNode = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
        } while (invalidSrcNode(srcNode));
        Node srcNodeP = srcNode.getParent();
        Node srcNodeS = getOtherChild(srcNodeP, srcNode);
        double t_srcNode = srcNode.getHeight();
        double t_srcNodeP = srcNodeP.getHeight();
        double t_srcNodeS = srcNodeS.getHeight();

        // Select destination branch node:
        Node destNode;
        do {
            destNode = tree.getNode(Randomizer.nextInt(tree.getNodeCount()));
        } while (invalidDestNode(srcNode, destNode));
        Node destNodeP = destNode.getParent();
        double t_destNode = destNode.getHeight();

        // Handle special cases involving root:

        if (destNode.isRoot()) {
            // FORWARD ROOT MOVE
            
            // Record srcNode grandmother height:
            double t_srcNodeG = srcNodeP.getParent().getHeight();

            // Choose new root height:
            double newTime = t_destNode+Randomizer.nextExponential(1.0/(alpha*t_destNode));

            // Implement tree changes:
            try {
                disconnectBranch(srcNode);
            } catch (RecolouringException ex) {
                if (cTree.discardWhenMaxExceeded()) {
                    ex.discardMsg();
                    return Double.NEGATIVE_INFINITY;
                } else
                    ex.throwRuntime();
            }
            setChangeCount(destNode, 0);
            connectBranchToRoot(srcNode, destNode, newTime);
            setRoot(srcNodeP);

            // Abort if colouring inconsistent:
            if (cTree.getFinalBranchColour(srcNode) != cTree.getFinalBranchColour(destNode))
                return Double.NEGATIVE_INFINITY;
            
            // Update colour of root node:
            setNodeColour(srcNodeP, cTree.getFinalBranchColour(srcNode));
            
            // Incorporate HR contribution of tree topology and node
            // height changes:
            return Math.log(alpha*t_destNode)
                    +(1.0/alpha)*(newTime/t_destNode-1.0)
                    -Math.log(t_srcNodeG-Math.max(t_srcNode, t_srcNodeS));

        }

        if (srcNodeP.isRoot()) {
            // BACKWARD ROOT MOVE
            
            // Abort if move would change root colour or truncate colour
            // changes. (This would be an irreversible move.)
            if (cTree.getChangeCount(srcNodeS)>0 ||
                    (cTree.getNodeColour(srcNodeS)
                    != cTree.getFinalBranchColour(srcNode)))
                return Double.NEGATIVE_INFINITY;
            
            // Record old srcNode parent height:
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
            setRoot(srcNodeS);
            
            // Abort if new colouring is inconsistent:
            if (cTree.getNodeColour(srcNodeP) != cTree.getFinalBranchColour(srcNode))
                return Double.NEGATIVE_INFINITY;
            
            // Incorporate HR contribution of tree topology and node
            // height changes:
            return Math.log(t_destNodeP-Math.max(t_srcNode, t_destNode))
                    -Math.log(alpha*t_srcNodeS)
                    -(1.0/alpha)*(oldTime/t_srcNodeS-1.0);
            
        }

        // NON-ROOT MOVE

        // Record srcNode grandmother height:
        double t_srcNodeG = srcNodeP.getParent().getHeight();

        // Choose height of new attachment point:
        double min_newTime = Math.max(t_destNode, t_srcNode);
        double t_destNodeP = destNodeP.getHeight();
        double span = t_destNodeP-min_newTime;
        double newTime = min_newTime+span*Randomizer.nextDouble();

        // Implement tree changes:
        try {
            disconnectBranch(srcNode);
        } catch (RecolouringException ex) {
            if (cTree.discardWhenMaxExceeded()) {
                ex.discardMsg();
                return Double.NEGATIVE_INFINITY;
            } else
                ex.throwRuntime();
        }
        connectBranch(srcNode, destNode, newTime);
        
        // Reject outright if new colouring inconsistent:
        if (cTree.getNodeColour(srcNodeP) != cTree.getFinalBranchColour(srcNode))
            return Double.NEGATIVE_INFINITY;

        // Incorporate HR contribution of tree topology and node
        // height changes:
        return Math.log(t_destNodeP-Math.max(t_srcNode, t_destNode))
                -Math.log(t_srcNodeG-Math.max(t_srcNode, t_srcNodeS));

    }

    /**
     * Returns true if srcNode CANNOT be used for the CWBR move.
     *
     * @param srcNode
     * @return True if srcNode invalid.
     */
    private boolean invalidSrcNode(Node srcNode) {

        if (srcNode.isRoot())
            return true;

        Node parent = srcNode.getParent();

        // This check is important in avoiding situations where it is
        // impossible to choose a valid destNode:
        if (parent.isRoot()) {

            Node sister = getOtherChild(parent, srcNode);

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
    private boolean invalidDestNode(Node srcNode, Node destNode) {

        if (destNode==srcNode
                ||destNode==srcNode.getParent()
                ||destNode.getParent()==srcNode.getParent())
            return true;

        Node srcNodeP = srcNode.getParent();
        Node destNodeP = destNode.getParent();

        if (destNodeP!=null&&(destNodeP.getHeight()<=srcNode.getHeight()))
            return true;

        return false;
    }
}
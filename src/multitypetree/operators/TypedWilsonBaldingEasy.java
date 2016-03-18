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
package multitypetree.operators;

import beast.core.Description;
import beast.core.Input;
import beast.evolution.tree.MultiTypeNode;
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
public class TypedWilsonBaldingEasy extends MultiTypeTreeOperator {

    public Input<Double> alphaInput = new Input<Double>("alpha",
            "Root height proposal parameter", .1);
    private double alpha;

    @Override
    public void initAndValidate() {
        super.initAndValidate();

        alpha = alphaInput.get();
    }

    @Override
    public double proposal() {
        // Check that operator can be applied to tree:
        if (mtTree.getLeafNodeCount()<3)
            throw new IllegalStateException("Tree too small for"
                    +" ColouredWilsonBaldingRandom operator.");

        // Select source node:
        Node srcNode;
        do {
            srcNode = mtTree.getNode(Randomizer.nextInt(mtTree.getNodeCount()));
        } while (invalidSrcNode(srcNode));
        Node srcNodeP = srcNode.getParent();
        Node srcNodeS = getOtherChild(srcNodeP, srcNode);
        double t_srcNode = srcNode.getHeight();
        double t_srcNodeP = srcNodeP.getHeight();
        double t_srcNodeS = srcNodeS.getHeight();

        // Select destination branch node:
        Node destNode;
        do {
            destNode = mtTree.getNode(Randomizer.nextInt(mtTree.getNodeCount()));
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
            disconnectBranch(srcNode);
            ((MultiTypeNode)destNode).clearChanges();
            connectBranchToRoot(srcNode, destNode, newTime);
            mtTree.setRoot(srcNodeP);
            
            // Abort if colouring inconsistent:
            if (((MultiTypeNode)srcNode).getFinalType()
                    != ((MultiTypeNode)destNode).getFinalType())
                return Double.NEGATIVE_INFINITY;
            
            // Update colour of root node:
            ((MultiTypeNode)srcNodeP).setNodeType(((MultiTypeNode)srcNode).getFinalType());
            
            // Final test of tree validity:
            if (!mtTree.isValid())
                return Double.NEGATIVE_INFINITY;
            
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
            if (((MultiTypeNode)srcNodeS).getChangeCount()>0 ||
                    (((MultiTypeNode)srcNodeS).getNodeType()
                    != ((MultiTypeNode)srcNode).getFinalType()))
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
            mtTree.setRoot(srcNodeS);
            
            // Abort if new colouring is inconsistent:
            if (((MultiTypeNode)srcNodeP).getNodeType()
                    != ((MultiTypeNode)srcNode).getFinalType())
                return Double.NEGATIVE_INFINITY;
            
            // Final test of tree validity:
            if (!mtTree.isValid())
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
        disconnectBranch(srcNode);
        connectBranch(srcNode, destNode, newTime);
        
        // Reject outright if new colouring inconsistent:
        if (((MultiTypeNode)srcNodeP).getNodeType() != ((MultiTypeNode)srcNode).getFinalType())
            return Double.NEGATIVE_INFINITY;
        
        // Final test of tree validity:
        if (!mtTree.isValid())
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

        Node destNodeP = destNode.getParent();

        if (destNodeP!=null&&(destNodeP.getHeight()<=srcNode.getHeight()))
            return true;

        return false;
    }
}
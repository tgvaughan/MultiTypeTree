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
package multitypetree.operators;

import beast.core.Description;
import beast.core.Input;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.Node;
import beast.util.Randomizer;

/**
 * Wilson-Balding branch swapping operator applied to coloured trees. This
 * version simply assigns randomly chosen type changes to new branches.
 *
 * @author Tim Vaughan
 */
@Description("Implements the unweighted Wilson-Balding branch"
+"swapping move.  This move is similar to one proposed by WILSON"
+"and BALDING 1998 and involves removing a subtree and"
+"re-attaching it on a new parent branch. "
+"See <a href='http://www.genetics.org/cgi/content/full/161/3/1307/F1'>picture</a>."
+"This version generates random type changes along branches altered by"
+"the operator.")
public class TypedWilsonBaldingRandom extends RandomRetypeOperator {

    public Input<Double> alphaInput = new Input<Double>("alpha",
            "Root height proposal parameter", .1);
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
            
            // Record probability of current configuration:
            double logHR = getBranchTypeProb(srcNode);

            // Record srcNode grandmother height:
            double t_srcNodeG = srcNodeP.getParent().getHeight();

            // Choose new root height:
            double newTime = t_destNode+Randomizer.nextExponential(1.0/(alpha*t_destNode));

            // Implement tree changes:
            disconnectBranch(srcNode);
            connectBranchToRoot(srcNode, destNode, newTime);
            mtTree.setRoot(srcNodeP);

            // Recolour root branches, incorporating probability of new branch
            // into HR:
            logHR -= retypeRootBranches(srcNode);
            
            // Abort if colouring inconsistent:
            if (((MultiTypeNode)srcNodeP).getNodeType()
                    != ((MultiTypeNode)destNode).getFinalType())
                return Double.NEGATIVE_INFINITY;
            
            // Incorporate HR contribution of tree topology and node
            // height changes:
            logHR += Math.log(alpha*t_destNode)
                    +(1.0/alpha)*(newTime/t_destNode-1.0)
                    -Math.log(t_srcNodeG-Math.max(t_srcNode, t_srcNodeS));

            return logHR;
        }

        if (srcNodeP.isRoot()) {
            // BACKWARD ROOT MOVE

            // Record probability of current configuration:
            double logHR = getRootBranchTypeProb(srcNode);
            
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

            // Recolour new branch, incorporating probability of new branch
            // into HR:
            logHR -= retypeBranch(srcNode);
            
            // Abort if new colouring is inconsistent:
            if (((MultiTypeNode)srcNodeP).getNodeType()
                    != ((MultiTypeNode)srcNode).getFinalType())
                return Double.NEGATIVE_INFINITY;
            
            // Incorporate HR contribution of tree topology and node
            // height changes:
            logHR += Math.log(t_destNodeP-Math.max(t_srcNode, t_destNode))
                    -Math.log(alpha*t_srcNodeS)
                    -(1.0/alpha)*(oldTime/t_srcNodeS-1.0);
            
            return logHR;
        }

        // NON-ROOT MOVE
        
        // Record probability of old configuration:
        double logHR = getBranchTypeProb(srcNode);

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
        logHR -= retypeBranch(srcNode);
        
        // Reject outright if new colouring inconsistent:
        if (((MultiTypeNode)srcNodeP).getNodeType()
                != ((MultiTypeNode)srcNode).getFinalType())
            return Double.NEGATIVE_INFINITY;

        // Incorporate HR contribution of tree topology and node
        // height changes:
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
    
    /**
     * Retype branches between srcNode and the root (srcNode's
     * parent) and between the root and srcNode's sister with a rate fixed
     * by the tuning parameter mu.
     *
     * @param srcNode
     * @param nChangesNode
     * @param nChangesSister
     * @return Probability of branch typing.
     */
    private double retypeRootBranches(Node srcNode) {

        Node root = srcNode.getParent();
        Node srcNodeSister = getOtherChild(root, srcNode);

        // Recolour first branch:
        double logP = retypeBranch(srcNode);

        // Adjust colour of root node:
        ((MultiTypeNode)root).setNodeType(((MultiTypeNode)srcNode).getFinalType());

        return logP + retypeBranch(srcNodeSister);
    }

    /**
     * Get probability of the colouring along the branch between srcNode
     * and its parent, and between that parent and srcNode's sister.
     * @param srcNode
     * @return 
     */
    private double getRootBranchTypeProb(Node srcNode) {

        Node srcNodeS = getOtherChild(srcNode.getParent(), srcNode);

        double mu = muInput.get();
        double T = 2.0 * srcNode.getParent().getHeight()
                - srcNode.getHeight()
                - srcNodeS.getHeight();
        int n = ((MultiTypeNode)srcNode).getChangeCount()
                + ((MultiTypeNode)srcNodeS).getChangeCount();
        int N = mtTree.getNTypes();

        if (N == 0)
            return 0.0;
        else
            return -mu*T + n*Math.log(mu/(N-1));

    }
    
}
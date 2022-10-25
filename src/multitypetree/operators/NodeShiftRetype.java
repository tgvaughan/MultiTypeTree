/*
 * Copyright (C) 2013 Tim Vaughan <tgvaughan@gmail.com>
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

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.evolution.tree.Node;
import beast.base.util.Randomizer;
import multitypetree.evolution.tree.MultiTypeNode;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Operator implementing a very similar move to MultiTypeUniform, "
        + " with the difference that this operator draws new node positions"
        + " uniformly between the parent and oldest child heights and"
        + " retypes the modified branches.  Additionally, this operator"
        + " can act on the root node.")
public class NodeShiftRetype extends UniformizationRetypeOperator {
    
    public Input<Boolean> rootOnlyInput = new Input<>("rootOnly",
            "Always select root node for height adjustment.", false);
    
    public Input<Boolean> noRootInput = new Input<>("noRoot",
            "Never select root node for height adjustment.", false);
    
    public Input<Double> rootScaleFactorInput = new Input<>("rootScaleFactor",
            "Scale factor used in root height proposals. (Default 0.8)", 0.8);
    
    @Override
    public void initAndValidate() {
        super.initAndValidate();
        
        if (rootOnlyInput.get() && noRootInput.get())
            throw new IllegalArgumentException("rootOnly and noRoot inputs "
                    + "cannot both be set to true simultaneously.");
    }
    
    @Override
    public double proposal() {
        // Select internal node to adjust:
        Node node;
        if (rootOnlyInput.get())
            node = mtTree.getRoot();
        else
            do {
                node = mtTree.getNode(mtTree.getLeafNodeCount()
                        + Randomizer.nextInt(mtTree.getInternalNodeCount()));
            } while (noRootInput.get() && node.isRoot());
        
                
        // Generate relevant proposal:
        if (node.isRoot())
            return rootProposal(node);
        else
            return nonRootProposal(node);
    }
    
    /**
     * Root node proposal.
     * @param root
     * @return log of HR
     */
    private double rootProposal(Node root) {
        
        double logHR = 0.0;
        
        // Record probability of current typing:
        logHR += getBranchTypeProb(root.getLeft())
                + getBranchTypeProb(root.getRight());
        
        // Select new root height:
        double u = Randomizer.nextDouble();
        double f = u*rootScaleFactorInput.get() + (1-u)/rootScaleFactorInput.get();
        double oldestChildHeight = Math.max(
                root.getLeft().getHeight(),
                root.getRight().getHeight());
        root.setHeight(oldestChildHeight + f*(root.getHeight()-oldestChildHeight));
        logHR -= Math.log(f);
        
        // Select new root node type:
        ((MultiTypeNode)root).setNodeType(Randomizer.nextInt(migModel.getNTypes()));
        
        // Recolour branches below root:
        try {
            logHR -= retypeBranch(root.getLeft())
                    + retypeBranch(root.getRight());
        } catch (NoValidPathException e) {
            return Double.NEGATIVE_INFINITY;
        }
        
        return logHR;
    }
    
    /**
     * Non-root internal node proposal.
     * @param node
     * @return log of HR of move
     */
    private double nonRootProposal(Node node) {
        
        double logHR = 0.0;
        
        // Record probability of current colouring:
        logHR += getBranchTypeProb(node)
                + getBranchTypeProb(node.getLeft())
                + getBranchTypeProb(node.getRight());
        
        // Select new node height:        
        double upperBound = node.getParent().getHeight();
        double lowerBound = Math.max(
                node.getLeft().getHeight(),
                node.getRight().getHeight());
        node.setHeight(lowerBound+(upperBound-lowerBound)*Randomizer.nextDouble());
        
        // Select new node colour:
        ((MultiTypeNode)node).setNodeType(Randomizer.nextInt(migModel.getNTypes()));
        
        // Recolour branches connected to node:
        try {
            logHR -= retypeBranch(node)
                    + retypeBranch(node.getLeft())
                    + retypeBranch(node.getRight());
        } catch (NoValidPathException e) {
            return Double.NEGATIVE_INFINITY;
        }
        
        return logHR;        
    }
    
}

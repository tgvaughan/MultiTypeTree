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

import beast.core.Description;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.Node;
import beast.util.Randomizer;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Implements type change (migration) pair birth/death move "
        + "described by Ewing et al., Genetics (2004).")
public class TypePairBirthDeath extends MultiTypeTreeOperator {

    @Override
    public void initAndValidate() { }
    
    @Override
    public double proposal() {
        mtTree = multiTypeTreeInput.get();
        
        int n = mtTree.getLeafNodeCount();
        int m = mtTree.getTotalNumberOfChanges();
        
        // Select sub-edge at random:
        int edgeNum = Randomizer.nextInt(2*n - 2 + m);
        
        // Find edge that sub-edge lies on:
        Node selectedNode = null;
        for (Node node : mtTree.getNodesAsArray()) {
            if (node.isRoot())
                continue;

            if (edgeNum<((MultiTypeNode)node).getChangeCount()+1) {
                selectedNode = node;
                break;
            }
            edgeNum -= ((MultiTypeNode)node).getChangeCount()+1;
        }
        
        // Complete either pair birth or pair death proposal:
        if (Randomizer.nextDouble()<0.5)
            return birthProposal(selectedNode, edgeNum, n, m);
        else
            return deathProposal(selectedNode, edgeNum, n, m);

    }
    
    /**
     * Type change pair birth proposal.
     * 
     * @param node Node above which selected edge lies
     * @param edgeNum Number of selected edge
     * @param n Number of nodes on tree.
     * @param m Number of type changes currently on tree.
     * @return log of Hastings factor of move.
     */
    private double birthProposal(Node node, int edgeNum, int n, int m) {
        
        MultiTypeNode mtNode = (MultiTypeNode)node;
        
        int ridx = edgeNum;
        int sidx = edgeNum-1;
        
        double ts, tr;
        int oldEdgeType;
        if (sidx<0) {
            ts = node.getHeight();
            oldEdgeType = mtNode.getNodeType();
        } else {
            ts = mtNode.getChangeTime(sidx);
            oldEdgeType = mtNode.getChangeType(sidx);
        }

        if (ridx>mtNode.getChangeCount()-1)
            tr = node.getParent().getHeight();
        else
            tr = mtNode.getChangeTime(ridx);

        int newEdgeType;
        do {
            newEdgeType = Randomizer.nextInt(mtTree.getNTypes());
        } while (newEdgeType == oldEdgeType);
        
        double tau1 = Randomizer.nextDouble()*(tr-ts) + ts;
        double tau2 = Randomizer.nextDouble()*(tr-ts) + ts;
        double tauMin = Math.min(tau1, tau2);
        double tauMax = Math.max(tau1, tau2);
        
        mtNode.insertChange(edgeNum, oldEdgeType, tauMax);
        mtNode.insertChange(edgeNum, newEdgeType, tauMin);
        
        return Math.log((mtTree.getNTypes()-1)*(m + 2*n - 2)*(tr-ts)*(tr-ts))
                - Math.log(2*(m + 2*n));
    }
    
    /**
     * Colour change pair death proposal.
     * 
     * @param node Node above which selected edge lies
     * @param edgeNum Number of selected edge
     * @param n Number of nodes on tree
     * @param m Number of colour changes currently on tree
     * @return log of Hastings factor of move.
     */
    private double deathProposal(Node node, int edgeNum, int n, int m) {
        
        MultiTypeNode mtNode = (MultiTypeNode)node;
        
        int idx = edgeNum-1;
        int sidx = edgeNum-2;
        int ridx = edgeNum+1;
        
        if (sidx<-1 || ridx > mtNode.getChangeCount())
            return Double.NEGATIVE_INFINITY;
        
        double ts, tr;
        int is, ir;
        if (sidx<0) {
            ts = node.getHeight();
            is = mtNode.getNodeType();
        } else {
            ts = mtNode.getChangeTime(sidx);
            is = mtNode.getChangeType(sidx);
        }
        
        if (ridx>mtNode.getChangeCount()-1)
            tr = node.getParent().getHeight();
        else
            tr = mtNode.getChangeTime(ridx);
        ir = mtNode.getChangeType(ridx-1);
        
        if (is != ir)
            return Double.NEGATIVE_INFINITY;
        
        mtNode.removeChange(idx);
        mtNode.removeChange(idx);
        
        return Math.log(2*(m + 2*n - 2))
                - Math.log((mtTree.getNTypes()-1)*(m+2*n-4)*(tr-ts)*(tr-ts));
    }
    
}

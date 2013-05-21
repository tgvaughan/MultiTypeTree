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
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.Node;
import beast.util.Randomizer;

/**
 * 
 * @author Denise Kuhnert
 */
@Description("Randomly selects true internal tree node (i.e. not the root) and"
        + " moves node height uniformly in interval restricted by the node's"
        + " parent and children.")
public class MultiTypeUniform extends MultiTypeTreeOperator {

    public Input<Boolean> includeRootInput = new Input<Boolean>("includeRoot",
            "Allow modification of root node.", false);
    
    public Input<Double> rootScaleFactorInput = new Input<Double>("rootScaleFactor",
            "Root scale factor.", 0.1);
    
    @Override
    public void initAndValidate() {
    }

    /**
     * Change the node height and return the hastings ratio.
     *
     * @return log of Hastings Ratio
     */
    @Override
    public double proposal() {

        mtTree = multiTypeTreeInput.get();
        
        int nodeSetSize = mtTree.getInternalNodeCount();
        if (!includeRootInput.get())
            nodeSetSize -= 1;

        // Randomly select event on tree:
        int event = Randomizer.nextInt(nodeSetSize + mtTree.getTotalNumberOfChanges());
        
        
        MultiTypeNode node = null;
        int changeIdx = -1;
        if (event<nodeSetSize)
            node = (MultiTypeNode)mtTree.getNode(mtTree.getLeafNodeCount() + event);
        else {
            event -= nodeSetSize;
            for (Node thisNode : mtTree.getNodesAsArray()) {
                if (thisNode.isRoot())
                    continue;                
                if (event<((MultiTypeNode)thisNode).getChangeCount()) {
                    node = (MultiTypeNode)thisNode;
                    changeIdx = event;
                    break;
                }
                event -= ((MultiTypeNode)thisNode).getChangeCount();
            }
        }
        
        if (node == null)
            throw new IllegalStateException("Event selection loop fell through!");
        
        if (changeIdx==-1) {
            if (node.isRoot()) {
                // Scale distance root and closest event
                double tmin = Math.max(((MultiTypeNode)node.getLeft()).getFinalChangeTime(),
                        ((MultiTypeNode)node.getRight()).getFinalChangeTime());
                
                double u = Randomizer.nextDouble();
                double f = u*rootScaleFactorInput.get()
                        + (1-u)/rootScaleFactorInput.get();
                
                double tnew = tmin + f*(node.getHeight()-tmin);
                
                node.setHeight(tnew);
                return -Math.log(f);
            } else {
                // Reposition node randomly between closest events
                double tmin = Math.max(((MultiTypeNode)node.getLeft()).getFinalChangeTime(),
                        ((MultiTypeNode)node.getLeft()).getFinalChangeTime());
                
                double tmax = node.getChangeCount()>0
                        ? node.getChangeTime(0)
                        : node.getParent().getHeight();
                
                double u = Randomizer.nextDouble();
                double tnew = u*tmin + (1-u)*tmax;
                
                node.setHeight(tnew);
                return 0.0;
            }
        } else {
            double tmin, tmax;
            if (changeIdx+1<node.getChangeCount()) 
                tmax = node.getChangeTime(changeIdx+1);
            else
                tmax = node.getParent().getHeight();
            
            if (changeIdx-1<0)
                tmin = node.getHeight();
            else
                tmin = node.getChangeTime(changeIdx-1);
            
            double u = Randomizer.nextDouble();
            double tnew = u*tmin + (1-u)*tmax;
            
            node.setChangeTime(changeIdx, tnew);
            return 0.0;
        }
        
    }

}

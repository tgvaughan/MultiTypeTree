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
package beast.evolution.tree;

import beast.core.Description;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("A node in a multi-type phylogenetic tree.")
public class MultiTypeNode extends Node {
    
    int nTypeChanges = 0;
    List<Integer> changeTypes = new ArrayList<Integer>();
    List<Double> changeTimes = new ArrayList<Double>();
    int nodeType = 0;
    
    /**
     * Retrieve the total number of changes on the branch above this node.
     * @return type change count
     */
    public int getChangeCount() {
        return nTypeChanges;
    }
    
    /**
     * Obtain destination type (reverse time) of the change specified by idx
     * on the branch between this node and its parent.
     * 
     * @param idx
     * @return change type
     */
    public int getChangeType(int idx) {
        return changeTypes.get(idx);
    }
    
    /**
     * Obtain time of the change specified by idx on the branch between
     * this node and its parent.
     * 
     * @param idx
     * @return time of change
     */
    public double getChangeTime(int idx) {
        return changeTimes.get(idx);
    }
    
    /**
     * Retrieve final type on branch above this node.
     * 
     * @return final type
     */
    public int getFinalType() {
        if (nTypeChanges>0)
            return changeTypes.get(nTypeChanges-1);
        else
            return nodeType;
    }
    
    /**
     * Retrieve time of final type change on branch above this node,
     * or the height of the node if no changes exist.
     * 
     * @return final type change time
     */
    public double getFinalChangeTime() {
        if (nTypeChanges>0)
            return changeTimes.get(nTypeChanges-1);
        else
            return getHeight();
    }
    
    /**
     * Let tree know that it's been modified.
     */
    private void startEditing() {
        if (m_tree != null && m_tree.getState() != null) {
            m_tree.startEditing(null);
        }
    }
    
    /**
     * Sets type of node.
     * @param nodeType New node type.
     */
    public void setNodeType(int nodeType) {
        startEditing();
        this.nodeType = nodeType;
    }

    /**
     * Add a new type change to branch above node.
     * 
     * @param newType Destination (reverse time) type of change
     * @param time Time at which change occurs.
     */
    public void addChange(int newType, double time) {
        changeTypes.add(newType);
        changeTimes.add(time);
        nTypeChanges += 1;
    }
    
}

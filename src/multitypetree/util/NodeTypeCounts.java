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
package multitypetree.util;

import beast.core.*;
import beast.core.Input.Validate;
import beast.evolution.tree.MigrationModel;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.MultiTypeTree;
import beast.evolution.tree.Node;

import java.io.PrintStream;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Allows logging and defining distributions over number of"
        + " each node type on a multi-type tree.")
public class NodeTypeCounts extends CalculationNode implements Function, Loggable {

    public Input<MultiTypeTree> multiTypeTreeInput = new Input<>(
            "multiTypeTree",
            "Multi-type tree whose changes will be counted.",
            Validate.REQUIRED);

    public Input<MigrationModel> migrationModelInput = new Input<>(
        "migrationModel",
        "Migration model needed to specify number of demes.",
        Validate.REQUIRED);

    public Input<Boolean> internalOnlyInput = new Input<>(
            "internalNodesOnly",
            "Only count types of internal nodes.",
            true);

    public Input<Boolean> useCacheInput = new Input<>(
            "useCache", "Cache counts, updating only when tree changes. "
            + "Warning: this will cause problems if this TypeChangeCounts "
            + "is not used in the target distribution.", false);

    private MultiTypeTree mtTree;

    private int nTypes;

    private int[] nodeTypeCounts;
    private boolean useCache, dirty;

    public NodeTypeCounts() { };
    
    @Override
    public void initAndValidate() {
        mtTree = multiTypeTreeInput.get();
        nTypes = migrationModelInput.get().getNTypes();
        
        nodeTypeCounts = new int[nTypes];
        
        useCache = useCacheInput.get();
        dirty = true;
        update();
    }
    
    /**
     * Update type change count array as necessary.
     */
    private void update() {
        if (!dirty)
            return;
        
        // Zero count array
        for (int i=0; i<nodeTypeCounts.length; i++)
            nodeTypeCounts[i] = 0;
        
        // Recalculate array elements
        if (internalOnlyInput.get()) {
            for (Node node : mtTree.getInternalNodes())
                nodeTypeCounts[((MultiTypeNode) node).getNodeType()] += 1;
        } else {
            for (Node node : mtTree.getNodesAsArray())
                nodeTypeCounts[((MultiTypeNode) node).getNodeType()] += 1;
        }

        if (useCache)
            dirty = false;
    }

    @Override
    public int getDimension() {
        return nTypes;
    }

    @Override
    public double getArrayValue() {
        update();
        return nodeTypeCounts[0];
    }

    @Override
    public double getArrayValue(int iDim) {
        if (iDim<getDimension()) {
            update();
            return nodeTypeCounts[iDim];
        } else
            return Double.NaN;
    }

    @Override
    public void init(PrintStream out) throws Exception {
        
        String idString = mtTree.getID();

        for (int type = 0; type < nTypes; type++)
            out.print(idString + ".count_" + mtTree.getTypeString(type) + "\t");
    }

    @Override
    public void log(int nSample, PrintStream out) {
        update();
        
        for (int type = 0; type < nTypes; type++)
            out.print(nodeTypeCounts[type] + "\t");
    }

    @Override
    public void close(PrintStream out) { }
    
    @Override
    public boolean requiresRecalculation() {
        dirty = true;
        return true;
    }
    
}

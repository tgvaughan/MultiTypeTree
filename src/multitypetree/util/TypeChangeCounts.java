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

import beast.core.CalculationNode;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Loggable;
import beast.core.Function;
import beast.evolution.tree.MigrationModel;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.MultiTypeTree;
import beast.evolution.tree.Node;
import java.io.PrintStream;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Allows logging and defining distributions over number of"
        + " type changes on a multi-type tree.")
public class TypeChangeCounts extends CalculationNode implements Function, Loggable {

    public Input<MultiTypeTree> multiTypeTreeInput = new Input<>(
            "multiTypeTree",
            "Multi-type tree whose changes will be counted.",
            Validate.REQUIRED);

    public Input<MigrationModel> migrationModelInput = new Input<>(
        "migrationModel",
        "Migration model needed to specify number of demes.",
        Validate.REQUIRED);
    
    public Input<Boolean> useCacheInput = new Input<>(
            "useCache", "Cache counts, updating only when tree changes. "
            + "Warning: this will cause problems if this TypeChangeCounts "
            + "is not used in the target distribution.", false);
    
    private MultiTypeTree mtTree;
    
    private int nTypes;
    
    private int[] typeChanges;
    private boolean useCache, dirty;
    
    public TypeChangeCounts() { };
    
    @Override
    public void initAndValidate() {
        mtTree = multiTypeTreeInput.get();
        nTypes = migrationModelInput.get().getNTypes();
        
        typeChanges = new int[nTypes*(nTypes-1)];
        
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
        
        // Zero type change count array
        for (int i=0; i<typeChanges.length; i++)
            typeChanges[i] = 0;
        
        // Recalculate array elements
        for (Node node : mtTree.getNodesAsArray()) {
            if (node.isRoot()) {
                continue;
            }

            MultiTypeNode mtNode = (MultiTypeNode)node;
            int lastType = mtNode.getNodeType();
            for (int i = 0; i < mtNode.getChangeCount(); i++) {
                int nextType = mtNode.getChangeType(i);
                typeChanges[getOffset(lastType, nextType)] += 1;
                lastType = nextType;
            }
        }
        
        if (useCache)
            dirty = false;
    }
    
    /**
     * Retrieve offset into type change count array
     * 
     * @param i from type
     * @param j to type
     * @return offset
     */
    private int getOffset(int i, int j) {
        if (i==j)
            throw new RuntimeException("Programmer error: requested type "
                    + "change count array offset for diagonal element of "
                    + "type change count matrix.");
        
        if (j>i)
            j -= 1;
        return i*(nTypes-1)+j;
    }
    
    @Override
    public int getDimension() {
        return nTypes*(nTypes-1);
    }

    @Override
    public double getArrayValue() {
        update();
        return typeChanges[0];
    }

    @Override
    public double getArrayValue(int iDim) {
        if (iDim<getDimension()) {
            update();
            return typeChanges[iDim];
        } else
            return Double.NaN;
    }

    @Override
    public void init(PrintStream out) throws Exception {
        
        String idString = mtTree.getID();

        for (int type = 0; type < nTypes; type++) {
            for (int typeP = 0; typeP < nTypes; typeP++) {
                if (type == typeP) {
                    continue;
                }
                out.print(idString + ".count_" + type + "_to_" + typeP + "\t");
            }
        }
    }

    @Override
    public void log(int nSample, PrintStream out) {
        update();
        
        for (int type = 0; type < nTypes; type++) {
            for (int typeP = 0; typeP < nTypes; typeP++) {
                if (type == typeP) {
                    continue;
                }
                out.print(typeChanges[getOffset(type, typeP)] + "\t");
            }
        }
    }

    @Override
    public void close(PrintStream out) { }
    
    @Override
    public boolean requiresRecalculation() {
        dirty = true;
        return true;
    }
    
}

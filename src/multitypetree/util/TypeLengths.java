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
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.MultiTypeTree;
import beast.evolution.tree.Node;
import beast.evolution.tree.SCMigrationModel;

import java.io.PrintStream;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Allows logging and defining distributions over the lengths of"
        + " time lineages spend in each type on a multi-type tree.")
public class TypeLengths extends CalculationNode implements Function, Loggable {

    public Input<MultiTypeTree> multiTypeTreeInput = new Input<>(
            "multiTypeTree",
            "Multi-type tree whose type-associated lengths will be recorded.",
            Validate.REQUIRED);

    public Input<SCMigrationModel> migrationModelInput = new Input<>(
        "migrationModel",
        "Migration model needed to specify number of demes.",
        Validate.REQUIRED);

    private MultiTypeTree mtTree;

    private int nTypes;

    private double[] typeLengths;

    public TypeLengths() { }
    
    @Override
    public void initAndValidate() {
        mtTree = multiTypeTreeInput.get();
        nTypes = migrationModelInput.get().getNTypes();
        
        typeLengths = new double[nTypes];
        
        update();
    }
    
    /**
     * Update type change count array as necessary.
     */
    private void update() {

        // Zero type change count array
        for (int i=0; i<typeLengths.length; i++)
            typeLengths[i] = 0.0;
        
        // Recalculate array elements
        for (Node node : mtTree.getNodesAsArray()) {
            if (node.isRoot()) {
                continue;
            }

            MultiTypeNode mtNode = (MultiTypeNode)node;
            int thisType = mtNode.getNodeType();
            double lastTime = mtNode.getHeight();
            for (int i = 0; i < mtNode.getChangeCount(); i++) {
                int nextType = mtNode.getChangeType(i);
                double nextTime = mtNode.getChangeTime(i);
                typeLengths[thisType] += (nextTime - lastTime);
                thisType = nextType;
                lastTime = nextTime;
            }

            typeLengths[thisType] += mtNode.getParent().getHeight() - lastTime;
        }
    }
    
    @Override
    public int getDimension() {
        return nTypes*(nTypes-1);
    }

    @Override
    public double getArrayValue() {
        update();
        return typeLengths[0];
    }

    @Override
    public double getArrayValue(int iDim) {
        if (iDim<getDimension()) {
            update();
            return typeLengths[iDim];
        } else
            return Double.NaN;
    }

    @Override
    public void init(PrintStream out) {
        
        String idString = mtTree.getID();

        if (!mtTree.hasTypeTrait() || mtTree.getTypeList().size() != nTypes) {
            throw new IllegalArgumentException(
                    "TypeLengths logger needs MultiTypeTree to have named types,\n"
                    + "and as many named types as there are demes in the model.");
        }

        for (int type = 0; type < nTypes; type++)
            out.print(idString + ".length_" + mtTree.getTypeString(type) + "\t");
    }

    @Override
    public void log(int nSample, PrintStream out) {
        update();
        
        for (int type = 0; type < nTypes; type++) {
            out.print(typeLengths[type] + "\t");
        }
    }

    @Override
    public void close(PrintStream out) { }
    
    @Override
    public boolean requiresRecalculation() {
        return true;
    }
    
}

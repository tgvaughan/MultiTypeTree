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
import beast.evolution.tree.SCMigrationModel;
import beast.util.Randomizer;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Swaps two types on a tree, including migration matrix elements "
        + "and population sizes.")
public class TypeSwap extends UniformizationRetypeOperator {

    SCMigrationModel migModelSC;

    @Override
    public void initAndValidate() {
        super.initAndValidate();

        if (!(migModel instanceof SCMigrationModel))
            throw new IllegalArgumentException("TypeSwap only applicable to " +
                    "the structured coalescent model.");

        migModelSC = (SCMigrationModel) migModel;
    }

    @Override
    public double proposal() {
        double logHR = 0.0;

        // Select types to swap:
        int typeA = Randomizer.nextInt(migModelSC.getNTypes());
        int typeB;
        do {
            typeB = Randomizer.nextInt(migModelSC.getNTypes());
        } while (typeB == typeA);
        
        // Calculate probability of selecting leaf branch typings:
        for (Node leaf : mtTree.getExternalNodes()) {
            MultiTypeNode mtParent = (MultiTypeNode)leaf.getParent();
            if (mtParent.getNodeType() == typeA || mtParent.getNodeType() == typeB)
                logHR += getBranchTypeProb(leaf);
        }

        // Swap involved rows and columns of the migration matrix:
        for (int i=0; i<migModelSC.getNTypes(); i++) {
            if (i == typeA || i == typeB)
                continue;

            // Swap cols:
            double oldA = migModelSC.getBackwardRate(i, typeA);
            double oldB = migModelSC.getBackwardRate(i, typeB);
            migModelSC.setBackwardRate(i, typeA, oldB);
            migModelSC.setBackwardRate(i, typeB, oldA);
            
            // Swap rows:
            oldA = migModelSC.getBackwardRate(typeA, i);
            oldB = migModelSC.getBackwardRate(typeB, i);
            migModelSC.setBackwardRate(typeA, i, oldB);
            migModelSC.setBackwardRate(typeB, i, oldA);
        }
        
        double old1 = migModelSC.getBackwardRate(typeA, typeB);
        double old2 = migModelSC.getBackwardRate(typeB, typeA);
        migModelSC.setBackwardRate(typeB, typeA, old1);
        migModelSC.setBackwardRate(typeA, typeB, old2);
        
        // Swap population sizes:
        old1 = migModelSC.getPopSize(typeA);
        old2 = migModelSC.getPopSize(typeB);
        migModelSC.setPopSize(typeA, old2);
        migModelSC.setPopSize(typeB, old1);

        // Swap types on tree:
        for (Node node : mtTree.getInternalNodes()) {
            MultiTypeNode mtNode = (MultiTypeNode)node;
            if (mtNode.getNodeType() == typeA)
                mtNode.setNodeType(typeB);
            else {
                if (mtNode.getNodeType() == typeB)
                    mtNode.setNodeType(typeA);
            }
            
            for (int idx=0; idx<mtNode.getChangeCount(); idx++) {
                if (mtNode.getChangeType(idx)==typeA)
                    mtNode.setChangeType(idx, typeB);
                else {
                    if (mtNode.getChangeType(idx)==typeB)
                        mtNode.setChangeType(idx, typeA);
                }
            }
        }

        // Retype leaf branches:
        for (Node leaf : mtTree.getExternalNodes()) {
            MultiTypeNode mtParent = (MultiTypeNode)leaf.getParent();
            if (mtParent.getNodeType() == typeA || mtParent.getNodeType() == typeB) {
                try {
                    logHR -= retypeBranch(leaf);
                } catch (NoValidPathException e) {
                    return Double.NEGATIVE_INFINITY;
                }
            }
        }

        return logHR;
    }
    
}

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
package beast.evolution.operators;

import beast.core.Description;
import beast.evolution.migrationmodel.MigrationModel;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.Node;
import beast.util.Randomizer;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Swaps two types on a tree, including migration matrix elements.")
public class TypeSwap extends UniformizationRetypeOperator {
    
    @Override
    public void initAndValidate() { }
    
    @Override
    public double proposal() {
        
        mtTree = multiTypeTreeInput.get();
        MigrationModel migModel = migrationModelInput.get();

        double logHR = 0.0;
        
        // Select types to swap:
        int typeA = Randomizer.nextInt(mtTree.getNTypes());
        int typeB;
        do {
            typeB = Randomizer.nextInt(mtTree.getNTypes());
        } while (typeB == typeA);
        
        // Calculate probability of selecting leaf branch typings:
        for (Node leaf : mtTree.getExternalNodes())
            logHR += getBranchTypeProb(leaf);

        // Swap involved rows and columns of the migration matrix:
        for (int i=0; i<mtTree.getNTypes(); i++) {
            
            // Swap cols:
            double oldA = migModel.getRate(i, typeA);
            double oldB = migModel.getRate(i, typeB);
            migModel.setRate(i, typeA, oldB);
            migModel.setRate(i, typeB, oldA);
            
            // Swap rows:
            oldA = migModel.getRate(typeA, i);
            oldB = migModel.getRate(typeB, i);
            migModel.setRate(typeA, i, oldB);
            migModel.setRate(typeB, i, oldA);
        }
        
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
        for (Node leaf : mtTree.getExternalNodes())
            logHR -= retypeBranch(leaf);
        
        return logHR;
    }
    
}
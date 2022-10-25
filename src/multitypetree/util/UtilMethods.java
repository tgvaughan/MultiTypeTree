/*
 * Copyright (C) 2014 Tim Vaughan <tgvaughan@gmail.com>
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

import beast.base.inference.parameter.IntegerParameter;
import multitypetree.evolution.tree.SCMigrationModel;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class UtilMethods {
    
    public static double [] getSimulatedHeights(SCMigrationModel migrationModel,
            IntegerParameter leafTypes) throws Exception {

        // Generate ensemble:
        int reps = 100000;
        double[] heights = new double[reps];

        for (int i = 0; i < reps; i++) {
            multitypetree.evolution.tree.StructuredCoalescentMultiTypeTree sctree;
            sctree = new multitypetree.evolution.tree.StructuredCoalescentMultiTypeTree();
            sctree.initByName(
                    "migrationModel", migrationModel,
                    "leafTypes", leafTypes);

            heights[i] = sctree.getRoot().getHeight();
        }

        return heights;
    }
}

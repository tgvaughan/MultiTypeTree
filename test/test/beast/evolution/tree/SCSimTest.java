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
package test.beast.evolution.tree;

import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.SCMigrationModel;
import beast.evolution.tree.TypeSet;
import beast.math.statistic.DiscreteStatistics;
import beast.util.Randomizer;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class SCSimTest {
    
    @Test
    public void test() throws Exception {
        
         System.out.println("SCSim test");
         
         Randomizer.setSeed(42);
        
        // Set up migration model.
        RealParameter rateMatrix = new RealParameter();
        rateMatrix.initByName(
                "dimension", 2,
                "value", "0.1 0.1");
        RealParameter popSizes = new RealParameter();
        popSizes.initByName(
                "value", "7.0 7.0");
        SCMigrationModel migrationModel = new SCMigrationModel();
        migrationModel.initByName(
                "rateMatrix", rateMatrix,
                "popSizes", popSizes,
                "typeSet", new TypeSet("A", "B"));

        // Specify leaf types:
        IntegerParameter leafTypes = new IntegerParameter();
        leafTypes.initByName(
                "value", "0 0 0");

        // Generate ensemble:
        int reps = 100000;
        double[] heights = new double[reps];

        for (int i = 0; i < reps; i++) {
            beast.evolution.tree.StructuredCoalescentMultiTypeTree sctree;
            sctree = new beast.evolution.tree.StructuredCoalescentMultiTypeTree();
            sctree.initByName(
                    "migrationModel", migrationModel,
                    "leafTypes", leafTypes);

            heights[i] = sctree.getRoot().getHeight();
        }

        double meanHeights = DiscreteStatistics.mean(heights);
        double varHeights = DiscreteStatistics.variance(heights);
        
        boolean withinTol = (Math.abs(meanHeights-19.2)<0.2)
                && (Math.abs(varHeights-310)<20);
        
        Assert.assertTrue(withinTol);

    }
    
}

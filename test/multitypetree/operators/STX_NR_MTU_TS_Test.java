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

import multitypetree.util.UtilMethods;
import beast.core.MCMC;
import beast.core.Operator;
import beast.core.State;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import multitypetree.distributions.StructuredCoalescentTreeDensity;
import beast.evolution.tree.MigrationModel;
import multitypetree.operators.MultiTypeTreeScale;
import multitypetree.operators.MultiTypeUniform;
import multitypetree.operators.NodeRetype;
import multitypetree.operators.TypedSubtreeExchange;
import beast.evolution.tree.MultiTypeTree;
import beast.evolution.tree.StructuredCoalescentMultiTypeTree;
import beast.math.statistic.DiscreteStatistics;
import beast.util.Randomizer;
import multitypetree.util.MultiTypeTreeStatLogger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class STX_NR_MTU_TS_Test {
 
    @Test
    public void test() throws Exception {
        System.out.println("STX_NR_MTU_TS test");
        
        // Test passing locally, not on Travis.  WHY!?
        
        // Fix seed.
        Randomizer.setSeed(53);
        
        // Assemble migration model:
        RealParameter rateMatrix = new RealParameter("0.1 0.1");
        RealParameter popSizes = new RealParameter("7.0 7.0");
        MigrationModel migModel = new MigrationModel();
        migModel.initByName(
                "rateMatrix", rateMatrix,
                "popSizes", popSizes);
        
        // Assemble initial MultiTypeTree
        MultiTypeTree mtTree = new StructuredCoalescentMultiTypeTree();
        mtTree.initByName(
                "typeLabel", "deme",
                "nTypes", 2,
                "migrationModel", migModel,
                "leafTypes","1 1 0 0");

        // Set up state:
        State state = new State();
        state.initByName("stateNode", mtTree);
        
        // Assemble distribution:
        StructuredCoalescentTreeDensity distribution =
                new StructuredCoalescentTreeDensity();
        distribution.initByName(
                "migrationModel", migModel,
                "multiTypeTree", mtTree);

        
        // Set up operators:
        Operator operatorSTX = new TypedSubtreeExchange();
        operatorSTX.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree,
                "migrationModel", migModel);
        
        Operator operatorNR = new NodeRetype();
        operatorNR.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree,
                "migrationModel", migModel);
        
        Operator operatorMTU = new MultiTypeUniform();
        operatorMTU.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree);
        
        Operator operatorMTTS = new MultiTypeTreeScale();
        operatorMTTS.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree,
                "scaleFactor", 1.5,
                "useOldTreeScaler", false);
        
        // Set up stat analysis logger:
        MultiTypeTreeStatLogger logger = new MultiTypeTreeStatLogger();
        logger.initByName(
                "multiTypeTree", mtTree,
                "burninFrac", 0.1,
                "logEvery", 1000);
        
        // Set up MCMC:
        MCMC mcmc = new MCMC();
        mcmc.initByName(
                "chainLength", "1000000",
                "state", state,
                "distribution", distribution,
                "operator", operatorSTX,
                "operator", operatorNR,
                "operator", operatorMTU,
                "operator", operatorMTTS,
                "logger", logger);
        
        // Run MCMC:
        mcmc.run();
        
        System.out.format("height mean = %s\n", logger.getHeightMean());
        System.out.format("height var = %s\n", logger.getHeightVar());
        System.out.format("height ESS = %s\n", logger.getHeightESS());
        
        // Direct simulation:
        double [] heights = UtilMethods.getSimulatedHeights(migModel,
                new IntegerParameter("1 1 0 0"));
        double simHeightMean = DiscreteStatistics.mean(heights);
        double simHeightVar = DiscreteStatistics.variance(heights);
        
        // Compare analysis results with truth:        
        boolean withinTol = (logger.getHeightESS()>500)
                && (Math.abs(logger.getHeightMean()-simHeightMean)<2.0)
                && (Math.abs(logger.getHeightVar()-simHeightVar)<50);
        
        Assert.assertTrue(withinTol);
    }
}

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
package test.beast.evolution.operators;

import beast.core.MCMC;
import beast.core.Operator;
import beast.core.State;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.coalescent.StructuredCoalescentTreeDensity;
import beast.evolution.tree.MigrationModel;
import beast.evolution.operators.MultiTypeTreeScale;
import beast.evolution.operators.MultiTypeUniform;
import beast.evolution.operators.NodeRetypeRandom;
import beast.evolution.operators.TypedSubtreeExchangeRandom;
import beast.evolution.tree.MultiTypeTree;
import beast.evolution.tree.StructuredCoalescentMultiTypeTree;
import beast.util.Randomizer;
import beast.util.unittesting.MultiTypeTreeStatLogger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class STXR_NRR_MTU_TS_Test_Excluded {
 
    @Test
    public void test() throws Exception {
        System.out.println("STXR_NRR_MTU_TS test");
        
        // Fix seed.
        Randomizer.setSeed(42);
        
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
        Operator operatorSTXR = new TypedSubtreeExchangeRandom();
        operatorSTXR.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree,
                "mu", 0.2);
        
        Operator operatorNRR = new NodeRetypeRandom();
        operatorNRR.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree,
                "mu", 0.2);
        
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
                "chainLength", "10000000",
                "state", state,
                "distribution", distribution,
                "operator", operatorSTXR,
                "operator", operatorNRR,
                "operator", operatorMTU,
                "operator", operatorMTTS,
                "logger", logger);
        
        // Run MCMC:
        mcmc.run();
        
        System.out.format("height mean = %s\n", logger.getHeightMean());
        System.out.format("height var = %s\n", logger.getHeightVar());
        System.out.format("height ESS = %s\n", logger.getHeightESS());
        
        // Compare analysis results with truth:        
        boolean withinTol = (logger.getHeightESS()>2000)
                && (Math.abs(logger.getHeightMean()-25.8)<0.5)
                && (Math.abs(logger.getHeightVar()-320)<30);
        
        Assert.assertTrue(withinTol);
    }
}

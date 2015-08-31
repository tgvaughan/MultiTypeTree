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

import beast.core.MCMC;
import beast.core.State;
import beast.core.parameter.RealParameter;
import multitypetree.distributions.StructuredCoalescentTreeDensity;
import beast.evolution.tree.SCMigrationModel;
import beast.evolution.tree.MultiTypeTreeFromNewick;
import beast.util.Randomizer;
import multitypetree.util.MultiTypeTreeStatLogger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class Ewing_Test {
 
    @Test
    public void test1() throws Exception {
        System.out.println("Ewing_test1");
        
        // Fix seed.
        Randomizer.setSeed(1);
        
        // Assemble initial MultiTypeTree
        String newickStr =
                "((1[&deme=0]:1,2[&deme=0]:1)[&deme=0]:1,"
                + "3[&deme=0]:2)[&deme=0]:0;";
        
        MultiTypeTreeFromNewick mtTree = new MultiTypeTreeFromNewick();
        mtTree.initByName(
                "value", newickStr,
                "typeLabel", "deme");
        
        // Assemble migration model:
        RealParameter rateMatrix = new RealParameter("0.1 0.1");
        RealParameter popSizes = new RealParameter("7.0 7.0");
        SCMigrationModel migModel = new SCMigrationModel();
        migModel.initByName(
                "rateMatrix", rateMatrix,
                "popSizes", popSizes);
        
        // Assemble distribution:
        StructuredCoalescentTreeDensity distribution =
                new StructuredCoalescentTreeDensity();
        distribution.initByName(
                "migrationModel", migModel,
                "multiTypeTree", mtTree);
        
        // Set up state:
        State state = new State();
        state.initByName("stateNode", mtTree);
        
        // Set up operators:
        TypedWilsonBaldingEasy twbOperator = new TypedWilsonBaldingEasy();
        twbOperator.initByName(
                "weight",1.0,
                "multiTypeTree", mtTree,
                "migrationModel", migModel);
        
        TypedSubtreeExchangeEasy tsxOperator = new TypedSubtreeExchangeEasy();
        tsxOperator.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree,
                "migrationModel", migModel,
                "isNarrow", true);
        
        MultiTypeUniform mtuOperator = new MultiTypeUniform();
        mtuOperator.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree,
                "migrationModel", migModel);
        
        MultiTypeTreeScale mttsOperator = new MultiTypeTreeScale();
        mttsOperator.initByName(
                "weight", 1.0,
                "scaleFactor", 0.8,
                "useOldTreeScaler", true,
                "multiTypeTree", mtTree,
                "migrationModel", migModel);
        
        TypePairBirthDeath tpbdOperator = new TypePairBirthDeath();
        tpbdOperator.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree,
                "migrationModel", migModel);
        
        TypeMergeSplit tmsOperator = new TypeMergeSplit();
        tmsOperator.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree,
                "migrationModel", migModel,
                "includeRoot", true);
        
        // Set up stat analysis logger:
        MultiTypeTreeStatLogger logger = new MultiTypeTreeStatLogger();
        logger.initByName(
                "multiTypeTree", mtTree,
                "burninFrac", 0.1,
                "logEvery", 1000);
        
//        Logger fileLogger = new Logger();
//        TreeHeightLogger thLogger = new TreeHeightLogger();
//        thLogger.initByName("tree", mtTree);
//        fileLogger.initByName(
//                "fileName", "test.log",
//                "logEvery", 1000,
//                "model", distribution,
//                "log", thLogger);
        
        // Set up MCMC:
        MCMC mcmc = new MCMC();
        mcmc.initByName(
                "chainLength", "10000000",
                "state", state,
                "distribution", distribution,
                "operator", twbOperator,
                "operator", tsxOperator,
                "operator", mtuOperator,
                "operator", mttsOperator,
                "operator", tpbdOperator,
                "operator", tmsOperator,
                "logger", logger);
        
        // Run MCMC:
        mcmc.run();
        
        System.out.format("height mean = %s\n", logger.getHeightMean());
        System.out.format("height var = %s\n", logger.getHeightVar());
        System.out.format("height ESS = %s\n", logger.getHeightESS());
        
        // Compare analysis results with truth:        
        boolean withinTol = (logger.getHeightESS()>3000)
                && (Math.abs(logger.getHeightMean()-19.15)<0.5)
                && (Math.abs(logger.getHeightVar()-310)<20);
        
        Assert.assertTrue(withinTol);
    }
    
    @Test
    public void test2() throws Exception {
        System.out.println("Ewing_test 2");
        
        // Fix seed.
        Randomizer.setSeed(42);
        
        // Assemble initial MultiTypeTree
        String newickStr =
                "(((1[&deme=1]:0.5)[&deme=0]:0.5,2[&deme=0]:1)[&deme=0]:1,"
                + "3[&deme=0]:2)[&deme=0]:0;";
        
        MultiTypeTreeFromNewick mtTree = new MultiTypeTreeFromNewick();
        mtTree.initByName(
                "value", newickStr,
                "typeLabel", "deme");
        
        // Assemble migration model:
        RealParameter rateMatrix = new RealParameter("0.1 0.1");
        RealParameter popSizes = new RealParameter("7.0 7.0");
        SCMigrationModel migModel = new SCMigrationModel();
        migModel.initByName(
                "rateMatrix", rateMatrix,
                "popSizes", popSizes);
        
        // Assemble distribution:
        StructuredCoalescentTreeDensity distribution =
                new StructuredCoalescentTreeDensity();
        distribution.initByName(
                "migrationModel", migModel,
                "multiTypeTree", mtTree,
                "checkValidity", true);
        
        // Set up state:
        State state = new State();
        state.initByName("stateNode", mtTree);
        
        // Set up operators:
        TypedWilsonBaldingEasy twbOperator = new TypedWilsonBaldingEasy();
        twbOperator.initByName(
                "weight",1.0,
                "migrationModel", migModel,
                "multiTypeTree", mtTree);
        
        TypedSubtreeExchangeEasy tsxOperator = new TypedSubtreeExchangeEasy();
        tsxOperator.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree,
                "migrationModel", migModel,
                "isNarrow", true);
        
        MultiTypeUniform mtuOperator = new MultiTypeUniform();
        mtuOperator.initByName(
                "weight", 1.0,
                "migrationModel", migModel,
                "multiTypeTree", mtTree);
        
        MultiTypeTreeScale mttsOperator = new MultiTypeTreeScale();
        mttsOperator.initByName(
                "weight", 1.0,
                "scaleFactor", 0.8,
                "useOldTreeScaler", true,
                "migrationModel", migModel,
                "multiTypeTree", mtTree);
        
        TypePairBirthDeath tpbdOperator = new TypePairBirthDeath();
        tpbdOperator.initByName(
                "weight", 1.0,
                "migrationModel", migModel,
                "multiTypeTree", mtTree);
        
        TypeMergeSplit tmsOperator = new TypeMergeSplit();
        tmsOperator.initByName(
                "weight", 1.0,
                "multiTypeTree", mtTree,
                "migrationModel", migModel,
                "includeRoot", true);
        
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
                "operator", twbOperator,
                "operator", tsxOperator,
                "operator", mtuOperator,
                "operator", mttsOperator,
                "operator", tpbdOperator,
                "operator", tmsOperator,
                "logger", logger);
        
        // Run MCMC:
        mcmc.run();
        
        System.out.format("height mean = %s\n", logger.getHeightMean());
        System.out.format("height var = %s\n", logger.getHeightVar());
        System.out.format("height ESS = %s\n", logger.getHeightESS());
        
        // Compare analysis results with truth:        
        boolean withinTol = (logger.getHeightESS()>1000)
                && (Math.abs(logger.getHeightMean()-23)<0.5)
                && (Math.abs(logger.getHeightVar()-300)<30.0);
        
        Assert.assertTrue(withinTol);
    }
}

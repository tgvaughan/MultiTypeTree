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

import beast.core.parameter.RealParameter;
import beast.evolution.migrationmodel.MigrationModel;
import beast.evolution.tree.MultiTypeTreeFromNewick;
import org.junit.Test;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class TWBtest {
 
    @Test
    public void testTWB() throws Exception {
        System.out.println("testTWB");
        
        // Assemble initial MultiTypeTree
        String newickStr =
                "((1[deme='1']:1,2[deme='0']:1)[deme='0']:1,"
                + "3[deme='0']:2)[deme='0']:0;";
        
        MultiTypeTreeFromNewick mtTree = new MultiTypeTreeFromNewick();
        mtTree.initByName(
                "newick", newickStr,
                "typeLabel", "deme",
                "nTypes", 2);
        
        // Assemble migration model:
        RealParameter rateMatrix = new RealParameter("0.1 0.1");
        RealParameter popSizes = new RealParameter("7.0 7.0");
        MigrationModel migModel = new MigrationModel();
        migModel.initByName(
                "rateMatrix", rateMatrix,
                "popSizes", popSizes);
    }
}

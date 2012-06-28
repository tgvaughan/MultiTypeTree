/*
 * Copyright (C) 2012 Tim Vaughan
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
package test.beast.evolution.likelihood;

import beast.evolution.likelihood.StructuredCoalescentLikelihood;
import beast.evolution.tree.ColouredTree;
import beast.evolution.tree.ColouredTreeFromNewick;
import beast.evolution.tree.Tree;
import beast.util.TreeParser;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Tim Vaughan
 */
public class StructuredCoalescentLikelihoodTest {

	/**
	 * Test of calculateLogP method, of class StructuredCoalescentLikelihood.
	 */
	@Test
	public void testCalculateLogP() throws Exception {
		System.out.println("calculateLogP");

		// Assemble test ColouredTree:
		Tree flatTree = new TreeParser(
				"((A[&state=0]:0.5,B[&state=0]:0.5)[&state=0]:1.5,"
				+ "(C[&state=0]:1.0,D[&state=0]:1.0)[&state=0]:1.0,)"
				+ "[&state=0]:0.0;");


		StructuredCoalescentLikelihood instance = new StructuredCoalescentLikelihood();
		double expResult = 0.0;
		double result = instance.calculateLogP();
		assertEquals(expResult, result, 0.0);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}

}

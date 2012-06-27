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
	public void testCalculateLogP() {
		System.out.println("calculateLogP");
		StructuredCoalescentLikelihood instance = new StructuredCoalescentLikelihood();
		double expResult = 0.0;
		double result = instance.calculateLogP();
		assertEquals(expResult, result, 0.0);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}

}

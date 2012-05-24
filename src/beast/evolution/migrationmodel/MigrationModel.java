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
package beast.evolution.migrationmodel;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Plugin;
import beast.core.parameter.RealParameter;

/**
 * Basic plugin describing a simple Markovian migration model, for use by
 * ColouredTree operators and likelihoods.  Note that this class and package
 * are just stubs.  We expect to have something similar to the
 * SubstitutionModel class/interface eventually.
 *
 * @author Tim Vaughan
 */
@Description("Basic plugin describing a simple Markovian migration model.")
public class MigrationModel extends Plugin {

	public Input<RealParameter> rateMatrixInput = new Input<RealParameter>(
			"rateMatrix", "Migration rate matrix", Validate.REQUIRED);

	RealParameter rateMatrix;

	public MigrationModel() { }

	@Override
	public void initAndValidate() {
		rateMatrix = rateMatrixInput.get();
	}
	
}

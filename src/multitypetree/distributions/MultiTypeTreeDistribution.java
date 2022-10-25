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
package multitypetree.distributions;

import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import multitypetree.evolution.tree.MultiTypeTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 *
 * @author Tim Vaughan
 */
public abstract class MultiTypeTreeDistribution extends Distribution {

	public Input<MultiTypeTree> mtTreeInput = new Input<>("multiTypeTree",
			"Multi-type tree.", Validate.REQUIRED);

    protected MultiTypeTree mtTree;

    @Override
    public void initAndValidate() {
        mtTree = mtTreeInput.get();
    }

    // Interface requirements:


    @Override
    public List<String> getArguments() {
        return null;
    }

    @Override
	public List<String> getConditions() {
		return null;
	}

	@Override
	public void sample(State state, Random random) {
	}
	
}

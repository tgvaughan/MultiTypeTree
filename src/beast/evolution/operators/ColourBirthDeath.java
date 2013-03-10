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
package beast.evolution.operators;

import beast.core.Description;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Implements the colour change (migration) birth/death move "
        + "described by Ewing et al., Genetics (2004).")
public class ColourBirthDeath extends MultiTypeTreeOperator {

    @Override
    public double proposal() {
        mtTree = multiTypeTreeInput.get();
        tree = mtTree.getUncolouredTree();
        
        // This move only works for more than 2 demes:
        if (mtTree.getNColours()<=2)
            return Double.NEGATIVE_INFINITY;

        int m = mtTree.getTotalNumberofChanges() + tree.getNodeCount() - 1;
        
        return 0;
    }
    
}

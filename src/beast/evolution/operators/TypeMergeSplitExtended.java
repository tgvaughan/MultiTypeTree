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
import beast.evolution.tree.MultiTypeTree;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Implementation of generalized merge-split operator described"
        + "by Ewing et al., 2004.")
public class TypeMergeSplitExtended extends MultiTypeTreeOperator {

    @Override
    public double proposal() {
        double logHR = 0.0;
        
        MultiTypeTree mtTree = multiTypeTreeInput.get();
        

        
        return logHR;
    }
    
}

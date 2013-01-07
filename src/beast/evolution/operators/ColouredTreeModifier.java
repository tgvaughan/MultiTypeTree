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
package beast.evolution.operators;

import beast.evolution.tree.ColouredTree;
import beast.evolution.tree.Node;

/**
 * Operator for modifying coloured trees.
 * 
 * Note that this isn't an operator in the usual sense: calling the proposal()
 * method raises an exception.  However, this object is useful for modifying
 * ColouredTree objects outside of MCMC chains.  This is something that
 * needs to be done occasionally (such as when building trees piece by
 * piece during initialisation.)
 * 
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class ColouredTreeModifier extends ColouredTreeOperator {
            
    /**
     * Constructor for a ColouredTreeModifier object.  
     * 
     * @param cTree Tree that will be the focus of this operator.
     */
    public ColouredTreeModifier(ColouredTree cTree) {
        this.cTree = cTree;
        this.tree = cTree.getUncolouredTree();
    }

    @Override
    public double proposal() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public void disconnectBranch(Node node) {
        try {
            super.disconnectBranch(node);
        } catch (RecolouringException ex) {
            ex.throwRuntime();
        }
    }
    
    @Override
    public void addChange(Node node, int newColour, double time) {
        try {
            super.addChange(node, newColour, time);
        } catch (RecolouringException ex) {
            ex.throwRuntime();
        }
    }
    
}

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
package multitypetree.util;

import beast.core.CalculationNode;
import beast.core.Description;
import beast.core.Input;
import beast.core.Function;
import beast.core.Input.Validate;
import beast.core.Loggable;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.MultiTypeTree;

import java.io.PrintStream;


/**
 * @author Denise Kuehnert
 *         Date: Feb 12, 2013
 *         Time: 2:09:02 PM
 */
@Description("Logger to report root type of a multi-type tree.")
public class TreeRootTypeLogger extends CalculationNode implements Loggable, Function {

    public Input<MultiTypeTree> multiTypeTreeInput = new Input<>(
            "multiTypeTree", "MultiTypeTree to report root type of.", Validate.REQUIRED);

    public Input<Boolean> logStringTypesInput = new Input<>("logStringTypes",
        "Use string type labels in log.  Warning: breaks tracer.", false);

    MultiTypeTree mtTree;
    
    @Override
    public void initAndValidate() {
        mtTree = multiTypeTreeInput.get();
    }

    @Override
    public void init(PrintStream out) throws Exception {
        if (getID() == null || getID().matches("\\s*")) {
            out.print(mtTree.getID() + ".rootColor\t");
        } else {
            out.print(getID() + "\t");
        }
    }

    @Override
    public void log(int nSample, PrintStream out) {
        if (logStringTypesInput.get())
            out.print(mtTree.getTypeString(((MultiTypeNode)mtTree.getRoot()).getNodeType()) + "\t");
        else
            out.print(((MultiTypeNode)mtTree.getRoot()).getNodeType() + "\t");
    }

    @Override
    public void close(PrintStream out) { };

    @Override
    public int getDimension() {
        return 1;
    }

    @Override
    public double getArrayValue() {
        return ((MultiTypeNode)mtTree.getRoot()).getNodeType();
    }

    @Override
    public double getArrayValue(int iDim) {
        return ((MultiTypeNode)mtTree.getRoot()).getNodeType();
    }
}


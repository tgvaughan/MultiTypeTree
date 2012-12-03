/*
 * Copyright (C) 2012 Tim Vaughan <tgvaughan@gmail.com>
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
import beast.core.Input;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.Node;
import beast.util.Randomizer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Scale operator for coloured trees.  Also allows additional "
        + "scalar parameters to be rescaled (either forward or inversely) "
        + "at the same time.")
public class ColouredTreeScale extends ColouredTreeOperator {
    
    public Input<List<RealParameter>> scalarParamsInput =
            new Input<List<RealParameter>>("scalarParam",
            "Scale this scalar parameter by the same amount as tree.",
            new ArrayList<RealParameter>());
    
    public Input<List<RealParameter>> scalarParamsInverseInput =
            new Input<List<RealParameter>>("scalarParamInverse",
            "Scale this scalar parameter inversely.",
            new ArrayList<RealParameter>());

    public Input<Double> scaleFactorInput = new Input<Double>("scaleFactor",
            "Scaling is restricted to the range [1/scaleFactor, scaleFactor]");

    @Override
    public void initAndValidate() {
    }

    @Override
    public double proposal() {

        cTree = colouredTreeInput.get();
        tree = cTree.getUncolouredTree();

        // Choose scale factor:
        double u = Randomizer.nextDouble();
        double f = u*scaleFactorInput.get()+(1.0-u)/scaleFactorInput.get();

        // Count scaled elements:
        int n = 0;

        // Scale internal node heights:
        for (Node node : tree.getInternalNodes()) {
            node.setHeight(node.getHeight()*f);
            n += 1;
        }

        // Ensure new node heights make sense, force reject if not:
        if (u<1.0&&!validateSubtreeNodeHeights(tree.getRoot()))
            return Double.NEGATIVE_INFINITY;

        // Scale colour change times:
        for (Node node : tree.getNodesAsArray()) {
            for (int c = 0; c<cTree.getChangeCount(node); c++) {
                double oldTime = cTree.getChangeTime(node, c);
                setChangeTime(node, c, f*oldTime);
                n += 1;
            }
        }
        
        // Scale scalar parameters:
        for (RealParameter scalar : scalarParamsInput.get()) {
            double oldValue = scalar.getValue();
            scalar.setValue(oldValue*f);
            n += 1;
        }
        
        for (RealParameter scalar : scalarParamsInverseInput.get()) {
            double oldValue = scalar.getValue();
            scalar.setValue(oldValue/f);
            n -= 1;
        }

        // Return Hastings ratio:
        return (n-2)*Math.log(f);
    }

    /**
     * Check whether the heights of all nodes in the subtree of node are smaller
     * than those of their parents.
     *
     * @param node
     * @return True if heights satisfy the condition.
     */
    public boolean validateSubtreeNodeHeights(Node node) {

        if (!node.isRoot())
            if (node.getHeight()>node.getParent().getHeight())
                return false;

        if (!node.isLeaf())
            for (Node child : node.getChildren())
                if (!validateSubtreeNodeHeights(child))
                    return false;

        return true;
    }
}

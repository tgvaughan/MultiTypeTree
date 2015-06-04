/*
 * Copyright (C) 2015 Tim Vaughan (tgvaughan@gmail.com)
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

import beast.core.Distribution;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.evolution.tree.MultiTypeTree;
import beast.evolution.tree.Tree;
import java.io.PrintStream;

/**
 * Used to log running estimate of MAP MultiTypeTree.
 *
 * @author Tim Vaughan (tgvaughan@gmail.com)
 */
public class MAPTreeLogger extends Tree {

    public Input<MultiTypeTree> multiTypeTreeInput = new Input<>(
        "multiTypeTree",
        "Multi-type tree state to maximize posterior wrt.",
        Validate.REQUIRED);

    public Input<Distribution> posteriorInput = new Input<>(
        "posterior",
        "Posterior used to identify MAP tree",
        Validate.REQUIRED);

    MultiTypeTree currentMAPTree;
    double maxPosterior;

    @Override
    public void initAndValidate() throws Exception {
        super.initAndValidate();

        currentMAPTree = multiTypeTreeInput.get().copy();
        currentMAPTree.setTypeTrait(multiTypeTreeInput.get().getTypeTrait());
        if (multiTypeTreeInput.get().typeTraitValuesInput.get().size()>0) {
            currentMAPTree.typeTraitValuesInput.setValue(
                    multiTypeTreeInput.get().typeTraitValuesInput.get(),
                    currentMAPTree);
        }
        currentMAPTree.initAndValidate();
        maxPosterior = Double.NEGATIVE_INFINITY;
    }

    @Override
    public void init(PrintStream out) throws Exception {
        currentMAPTree.init(out);
    }

    @Override
    public void log(int nSample, PrintStream out) {
        if (posteriorInput.get().getCurrentLogP()>maxPosterior) {
            maxPosterior = posteriorInput.get().getCurrentLogP();
            currentMAPTree.assignFrom(multiTypeTreeInput.get());
        }
        currentMAPTree.log(nSample, out);
    }

    @Override
    public void close(PrintStream out) {
        currentMAPTree.close(out);
    }
    
}

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
package beast.app.multitypetree.beauti;

import beast.app.beauti.BeautiDoc;
import beast.core.BEASTInterface;
import beast.core.parameter.Parameter;
import beast.core.parameter.RealParameter;
import beast.evolution.likelihood.TreeLikelihood;
import beast.evolution.tree.MigrationModel;
import beast.evolution.tree.MultiTypeTree;
import beast.evolution.tree.StructuredCoalescentMultiTypeTree;
import beast.util.Randomizer;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Class containing a static method used as a "custom connector" in the
 * MultiTypeTree BEAUti template.  This connector ensures that the
 * simulation used to produce the initial tree uses a migration model
 * containing the same rates and population sizes as the one specified
 * in the tree prior.
 *
 * @author Tim Vaughan (tgvaughan@gmail.com)
 */
public class InitMigrationModelConnector {
    
    public static boolean customConnector(BeautiDoc doc) {

        for (BEASTInterface p : doc.getPartitions("Tree")) {
            TreeLikelihood treeLikelihood = (TreeLikelihood) p;
            StructuredCoalescentMultiTypeTree tree =
                (StructuredCoalescentMultiTypeTree) treeLikelihood.treeInput.get();

            String pID = BeautiDoc.parsePartition(tree.getID());

            MigrationModel migModel = (MigrationModel)doc.pluginmap.get(
                "migModel.t:" + pID);

            MigrationModel migModelInit = (MigrationModel)doc.pluginmap.get(
                "migModelInit.t:" + pID);

            String rateMatrixStr = getParameterString(migModel.rateMatrixInput.get());
            String popSizesStr = getParameterString(migModel.popSizesInput.get());

            migModelInit.rateMatrixInput.get().valuesInput.setValue(
                rateMatrixStr,
                migModelInit.rateMatrixInput.get());

            migModelInit.popSizesInput.get().valuesInput.setValue(
                popSizesStr,
                migModelInit.popSizesInput.get());
        }

        return false;
    }

    private static String getParameterString(Parameter.Base param) {

        String str = "";
        for (Object value : (List<Object>) param.valuesInput.get()) {
            if (str.length()>0)
                str += " ";
            str += value;
        }

        return str;
    }
}

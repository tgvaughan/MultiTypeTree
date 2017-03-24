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
import beast.evolution.tree.SCMigrationModel;
import beast.evolution.tree.StructuredCoalescentMultiTypeTree;
import beast.evolution.tree.TraitSet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


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

    public static int uniqueTraitsInData(StructuredCoalescentMultiTypeTree scTree) {
        Set<String> uniqueTypes = new HashSet<>();
        TraitSet typeTraitSet = scTree.typeTraitInput.get();
        for (String taxonName : typeTraitSet.taxaInput.get().getTaxaNames())
            uniqueTypes.add(typeTraitSet.getStringValue(taxonName));

        return uniqueTypes.size();
    }
    
    public static boolean customConnector(BeautiDoc doc) {

        for (BEASTInterface p : doc.getPartitions("Tree")) {
            TreeLikelihood treeLikelihood = (TreeLikelihood) p;
            StructuredCoalescentMultiTypeTree tree =
                (StructuredCoalescentMultiTypeTree) treeLikelihood.treeInput.get();

            String pID = BeautiDoc.parsePartition(tree.getID());

            SCMigrationModel migModel = (SCMigrationModel)doc.pluginmap.get(
                "migModel.t:" + pID);

            SCMigrationModel migModelInit = (SCMigrationModel)doc.pluginmap.get(
                "migModelInit.t:" + pID);

            String rateMatrixStr = getParameterString((RealParameter)migModel.rateMatrixInput.get());
            String popSizesStr = getParameterString((RealParameter)migModel.popSizesInput.get());

            // Ensure model has minimum number of demes
            int uniqueTraitCount = uniqueTraitsInData(tree);
            StringBuilder rateMatrixStrBuilder = new StringBuilder();
            StringBuilder popSizesStrBuilder = new StringBuilder();
            if (migModel.getNTypes()<uniqueTraitCount) {
                for (int i=0; i<uniqueTraitCount; i++) {
                    popSizesStrBuilder.append(" 1.0");
                    for (int j=0; j<uniqueTraitCount; j++) {
                        if (j == i)
                            continue;

                        rateMatrixStrBuilder.append(" 1.0");
                    }
                }

                popSizesStr = popSizesStrBuilder.toString();
                rateMatrixStr = rateMatrixStrBuilder.toString();

                ((RealParameter)migModel.popSizesInput.get()).setDimension(uniqueTraitCount);
                ((RealParameter)migModel.popSizesInput.get()).valuesInput.setValue(popSizesStr,
                        (RealParameter)migModel.popSizesInput.get());

                ((RealParameter)migModel.rateMatrixInput.get()).setDimension(uniqueTraitCount*(uniqueTraitCount-1));
                ((RealParameter)migModel.rateMatrixInput.get()).valuesInput.setValue(rateMatrixStr,
                        (RealParameter)migModel.rateMatrixInput.get());

                ((RealParameter)migModel.popSizesInput.get()).initAndValidate();
                ((RealParameter)migModel.rateMatrixInput.get()).initAndValidate();
                migModel.initAndValidate();
            }

            ((RealParameter)migModelInit.popSizesInput.get()).setDimension(migModel.getNTypes());

            ((RealParameter)migModelInit.popSizesInput.get()).valuesInput.setValue(
                popSizesStr,
                    (RealParameter)migModelInit.popSizesInput.get());

            ((RealParameter)migModelInit.rateMatrixInput.get()).setDimension(
                migModel.getNTypes()*(migModel.getNTypes()-1));
            ((RealParameter)migModelInit.rateMatrixInput.get()).valuesInput.setValue(
                rateMatrixStr,
                    (RealParameter)migModelInit.rateMatrixInput.get());

            try {
                ((RealParameter)migModelInit.popSizesInput.get()).initAndValidate();
                ((RealParameter)migModelInit.rateMatrixInput.get()).initAndValidate();
                migModelInit.initAndValidate();
            } catch (Exception ex) {
                System.err.println("Error configuring initial migration model.");
            }
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

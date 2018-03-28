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

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Loggable;
import beast.evolution.tree.SCMigrationModel;
import beast.evolution.tree.MultiTypeTree;
import beast.evolution.tree.TypeSet;

import java.io.PrintStream;

/**
 *
 * @author Tim Vaughan (tgvaughan@gmail.com)
 */
public class MigrationModelLogger extends BEASTObject implements Loggable {

    public Input<SCMigrationModel> migModelInput = new Input<>("migrationModel",
        "Migration model to log.", Validate.REQUIRED);

    public Input<MultiTypeTree> multiTypeTreeInput = new Input<>(
        "multiTypeTree", "Tree from which to acquire type names.");

    private SCMigrationModel migModel;
    private MultiTypeTree mtTree;

    @Override
    public void initAndValidate() {
        migModel = migModelInput.get();
        mtTree = multiTypeTreeInput.get();
    }

    @Override
    public void init(PrintStream out) {
        String outName;
        TypeSet typeSet = migModel.getTypeSet();

        if (migModel.getID() == null || migModel.getID().matches("\\s*"))
            outName = "migModel";
        else
            outName = migModel.getID();

        out.print(outName + ".popSizeScaleFactor\t");

        for (int i=0; i<migModel.getNTypes(); i++) {
            if (mtTree != null)
                out.print(outName + ".popSize_" + typeSet.getTypeName(i) + "\t");
            else
                out.print(outName + ".popSize_" + i + "\t");
        }

        out.print(outName + ".rateMatrixScaleFactor\t");

        for (int i=0; i<migModel.getNTypes(); i++) {
            for (int j=0; j<migModel.getNTypes(); j++) {
                if (i==j)
                    continue;
                if (mtTree != null)
                    out.format("%s.rateMatrix_%s_%s\t", outName,
                        typeSet.getTypeName(i), typeSet.getTypeName(j));
                else
                    out.format("%s.rateMatrix_%d_%d\t", outName, i, j);
            }
        }
        
        if (migModel.rateMatrixFlagsInput.get() != null) {
            for (int i=0; i<migModel.getNTypes(); i++) {
                for (int j=0; j<migModel.getNTypes(); j++) {
                    if (i==j)
                        continue;
                    if (mtTree != null)
                        out.format("%s.rateMatrixFlag_%s_%s\t", outName,
                            typeSet.getTypeName(i), typeSet.getTypeName(j));
                    else
                        out.format("%s.rateMatrixFlag_%d_%d\t", outName, i, j);
                }
            }
        }
    }

    @Override
    public void log(long nSample, PrintStream out) {

        out.print(migModel.getPopSizeScaleFactor() + "\t");

        for (int i=0; i<migModel.getNTypes(); i++) {
            out.print(migModel.getPopSizeForLog(i) + "\t");
        }

        out.print(migModel.getRateScaleFactor() + "\t");

        for (int i=0; i<migModel.getNTypes(); i++) {
            for (int j=0; j<migModel.getNTypes(); j++) {
                if (i==j)
                    continue;
                out.print(migModel.getRateForLog(i, j) + "\t");
            }
        }
        
        if (migModel.rateMatrixFlagsInput.get() != null) {
            for (int i=0; i<migModel.getNTypes(); i++) {
                for (int j=0; j<migModel.getNTypes(); j++) {
                    if (i==j)
                        continue;
                    if (migModel.getRateFlag(i,j))
                        out.format("1\t");
                    else
                        out.format("0\t");
                }
            }
        }
    }

    @Override
    public void close(PrintStream out) {
    }
    
}

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
    public void initAndValidate() throws Exception {
        migModel = migModelInput.get();
        mtTree = multiTypeTreeInput.get();
    }

    @Override
    public void init(PrintStream out) throws Exception {
        String outName;
        if (migModel.getID() == null || migModel.getID().matches("\\s*"))
            outName = "migModel";
        else
            outName = migModel.getID();
        

        for (int i=0; i<migModel.getNTypes(); i++) {
            if (mtTree != null)
                out.print(outName + ".popSize_" + mtTree.getTypeString(i) + "\t");
            else
                out.print(outName + ".popSize_" + i + "\t");
        }

        for (int i=0; i<migModel.getNTypes(); i++) {
            for (int j=0; j<migModel.getNTypes(); j++) {
                if (i==j)
                    continue;
                if (mtTree != null)
                    out.format("%s.rateMatrix_%s_%s\t", outName,
                        mtTree.getTypeString(i), mtTree.getTypeString(j));
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
                            mtTree.getTypeString(i), mtTree.getTypeString(j));
                    else
                        out.format("%s.rateMatrixFlag_%d_%d\t", outName, i, j);
                }
            }
        }
    }

    @Override
    public void log(int nSample, PrintStream out) {
                        
        for (int i=0; i<migModel.getNTypes(); i++) {
            out.print(migModel.getPopSize(i) + "\t");
        }

        for (int i=0; i<migModel.getNTypes(); i++) {
            for (int j=0; j<migModel.getNTypes(); j++) {
                if (i==j)
                    continue;
                out.format("%g\t", migModel.getRateForLog(i, j));
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

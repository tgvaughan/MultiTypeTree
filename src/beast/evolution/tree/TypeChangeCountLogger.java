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
package beast.evolution.tree;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Loggable;
import beast.core.Plugin;
import java.io.PrintStream;

/**
 * Logger for total type-change count in multi-type trees.
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Log number of type-shifts along multi-type tree.")
public class TypeChangeCountLogger extends Plugin implements Loggable {

    public Input<MultiTypeTree> multiTypeTreeInput = new Input<MultiTypeTree>(
            "multiTypeTree",
            "Multi-type tree to log",
            Validate.REQUIRED);
    public Input<Boolean> logEachDirectionInput = new Input<Boolean>(
            "logEachDirection",
            "Whether to log number of changes between each type pair.",
            false);
    MultiTypeTree mtTree;
    boolean logEachDirection;

    public TypeChangeCountLogger() { };
	
    @Override
    public void initAndValidate() {
        logEachDirection = logEachDirectionInput.get();
        mtTree = multiTypeTreeInput.get();
    };

    @Override
    public void init(PrintStream out) throws Exception {

        String idString;
        if (getID() == null || getID().matches("\\s*")) {
            idString = mtTree.getID();
        } else {
            idString = getID();
        }

        // Print header:
        if (!logEachDirection) {
            out.print(idString + ".count\t");
        } else {
            for (int type = 0; type < mtTree.getNTypes(); type++) {
                for (int typeP = 0; typeP < mtTree.getNTypes(); typeP++) {
                    if (type == typeP) {
                        continue;
                    }
                    out.print(idString + ".count_" + type + "_to_" + typeP + "\t");
                }
            }
        }
    }

    @Override
    public void log(int nSample, PrintStream out) {

        if (!logEachDirection) {

            int count = 0;
            for (Node node : mtTree.getNodesAsArray()) {
                if (!node.isRoot()) {
                    count += ((MultiTypeNode)node).getChangeCount();
                }
            }

            out.print(Integer.toString(count) + "\t");

        } else {

            // Count migrations:
            int nTypes = mtTree.getNTypes();
            int[] typeChanges = new int[nTypes * nTypes];
            for (Node node : mtTree.getNodesAsArray()) {
                if (node.isRoot()) {
                    continue;
                }

                MultiTypeNode mtNode = (MultiTypeNode)node;
                int lastType = mtNode.getNodeType();
                for (int i = 0; i < mtNode.getChangeCount(); i++) {
                    int nextType = mtNode.getChangeType(i);
                    typeChanges[lastType + nTypes * nextType] += 1;
                    lastType = nextType;
                }
            }

            // Log results:
            for (int type = 0; type < nTypes; type++) {
                for (int typeP = 0; typeP < nTypes; typeP++) {
                    if (type == typeP) {
                        continue;
                    }
                    out.print(typeChanges[type + nTypes * typeP] + "\t");
                }
            }
        }
    }

    @Override
    public void close(PrintStream out) { };
}

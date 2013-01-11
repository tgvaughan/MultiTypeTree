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
public class ColourChangeCountLogger extends Plugin implements Loggable {
	
	public Input<ColouredTree> colouredTreeInput = new Input<ColouredTree>(
                "colouredTree",
                "Coloured tree to log",
                Validate.REQUIRED);
        
        public Input<Boolean> logEachDirectionInput = new Input<Boolean>(
                "logEachDirection",
                "Whether to log number of changes between each colour pair.",
                false);
        
        ColouredTree cTree;
        boolean logEachDirection;
        
	public ColourChangeCountLogger() { };
	
	@Override
	public void initAndValidate() {
            logEachDirection = logEachDirectionInput.get();
            cTree = colouredTreeInput.get();
        };

	@Override
	public void init(PrintStream out) throws Exception {

            String idString;
                if (getID() == null || getID().matches("\\s*"))
                    idString = cTree.getID();
                else
                    idString = getID();
                
		// Print header:
                if (!logEachDirection) {
                    out.print(idString + ".count\t");
                } else {
                    for (int c=0; c<cTree.getNColours(); c++) {
                        for (int cp=0; cp<cTree.getNColours(); cp++) {
                            if (c == cp)
                                continue;                            
                            out.print(idString + ".count_" + c + "to" + cp);
                        }
                    }
                }
	}

	@Override
	public void log(int nSample, PrintStream out) {

            int count = 0;
		for (Node node : cTree.getUncolouredTree().getNodesAsArray()) {
			if (!node.isRoot())
				count += cTree.getChangeCount(node);
		}
		
		out.print(count + "\t");
	}

	@Override
	public void close(PrintStream out) { };
}

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
package beast.evolution.operators;

import beast.core.Description;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.Node;
import beast.util.Randomizer;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Migration birth/death operator from Ewing et al., 2004.")
public class TypeChangeBirthDeath extends MultiTypeTreeOperator {

    @Override
    public void initAndValidate() { }
    
    @Override
    public double proposal() {
        mtTree = multiTypeTreeInput.get();
        
        // Select event at random:
        
        int event = Randomizer.nextInt(mtTree.getTotalNumberOfChanges()
                + mtTree.getInternalNodeCount());
        
        MultiTypeNode node = null;
        int changeIdx = -1;
        if (event < mtTree.getInternalNodeCount()) {
            node = (MultiTypeNode)mtTree.getNode(event+mtTree.getLeafNodeCount());
        } else {
            event -= mtTree.getInternalNodeCount();
            for (Node thisNode : mtTree.getNodesAsArray()) {
                if (thisNode.isRoot())
                    continue;
                
                if (event < ((MultiTypeNode)thisNode).getChangeCount()) {
                    node = (MultiTypeNode)thisNode;
                    changeIdx = event;
                    break;
                }
                
                event -= ((MultiTypeNode)thisNode).getChangeCount();
            }
        }
        
        if (Randomizer.nextBoolean())
            return birthMove(node, changeIdx);
        else
            return deathMove(node, changeIdx);
        
    }
    
    private double birthMove(MultiTypeNode node, int changeIdx) {
        double logHR = 0.0;
        
        return logHR;
    }
    
    private double deathMove(MultiTypeNode node, int changeIdx) {
        double logHR = 0.0;
        
        return logHR;
    }
    
}

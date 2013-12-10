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

import beast.core.Citation;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.evolution.tree.MigrationModel;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.Node;
import beast.util.Randomizer;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Joint migration/tree operator described by Beerli"
        + "and Felsenstein, Genetics, 1999.")
@Citation("Beerli and Felsenstein, Genetics 152:763 (1999).")
public class BeerliFelsenstein extends MultiTypeTreeOperator {
    
    public Input<MigrationModel> migModelInput = new Input<MigrationModel>(
            "migrationModel", "Migration model.", Validate.REQUIRED);
    
    protected MigrationModel migModel;
    
    protected enum EventType {COALESCENCE, SAMPLE, MIGRATION};    
    protected class TreeEvent {
        /**
         * Node associated with this event.
         */
        MultiTypeNode node;
        
        /**
         * Type of event.
         */
        EventType eventType;
        
        /**
         * Time of event, measured in age before most recent sample.
         */
        double time;
        
        int prevType, thisType;

    }
    protected List<TreeEvent> eventList;

    public BeerliFelsenstein() { }

    @Override
    public void initAndValidate() throws Exception {
        eventList = Lists.newArrayList();
        migModel = migModelInput.get();
        mtTree = multiTypeTreeInput.get();
    }
    
    @Override
    public double proposal() {
        assembleEventList();
        
        // Select non-root node at random:
        MultiTypeNode mtNode;
        do {
            mtNode = (MultiTypeNode)mtTree.getNode(Randomizer.nextInt(mtTree.getNodeCount()));
        } while (mtNode.isRoot());
        
        double logHR = getReverseMoveDensity(mtNode);
        
        return logHR;
    }
    
    /**
     * Retrieve proposal density of move which would result in a given
     * node being attached to its current parent and the migration path
     * along that edge.
     * 
     * @param node
     * @return log proposal density
     */
    public double getReverseMoveDensity(MultiTypeNode node) {
        double logP = 0.0;
        
        boolean started = false;
        int[] k = new int [migModel.getNDemes()];
        double t = 0;
        for (TreeEvent event : eventList) {
            
            switch (event.eventType) {
                case COALESCENCE:
                    k[event.node.getNodeType()] -= 1;
                    break;
                    
                case SAMPLE:
                    k[event.node.getNodeType()] += 1;
                    break;
                    
                case MIGRATION:
                    k[event.prevType] -= 1;
                    k[event.thisType] += 1;
                    break;
            }
            
            if (!started) {
                if (event.node.equals(node))
                    started = true;
                
                t = event.time;
                continue;
            }
            
            // Interval contribution
            double lambda = (k[event.thisType]-1)/migModel.getPopSize(event.thisType);
            for (int c=0; c<migModel.getNDemes(); c++) {
                if (c==event.thisType)
                    continue;
                
                lambda += migModel.getRate(event.thisType, c);
            }
            logP += (event.time - t)*lambda;

            // Check for end of edge
            if (event.node.equals(node.getParent())) {
                logP += Math.log(1.0/migModel.getPopSize(event.thisType));
                break;
            }

            // Update time of last event
            t = event.time;
        }
        
        return logP;
    }
    
    /**
     * Assemble list of events in tree.
     */
    private void assembleEventList() {
        
        eventList.clear();
        
        for (Node node : mtTree.getNodesAsArray()) {
            MultiTypeNode mtNode = (MultiTypeNode)node;
            
            TreeEvent event = new TreeEvent();
            event.node = mtNode;
            if (mtNode.isLeaf())
                event.eventType = EventType.SAMPLE;
            else
                event.eventType = EventType.COALESCENCE;
            event.time = mtNode.getHeight();
            event.thisType = mtNode.getNodeType();
            
            eventList.add(event);
            
            if (mtNode.isRoot())
                continue;
            
            int lastType = mtNode.getNodeType();
            for (int c=0; c<mtNode.getChangeCount(); c++) {
                TreeEvent mEvent = new TreeEvent();
                mEvent.node = mtNode;
                
                mEvent.eventType = EventType.MIGRATION;
                mEvent.time = mtNode.getChangeTime(c);
                mEvent.prevType = lastType;
                mEvent.thisType = mtNode.getChangeType(c);
                
                eventList.add(mEvent);
            }
        }
        
        // Sort events according to time:
        Collections.sort(eventList, new Comparator<BeerliFelsenstein.TreeEvent>() {

            @Override
            public int compare(TreeEvent e1, TreeEvent e2) {
                if (e1.time < e2.time)
                    return -1;
                
                if (e1.time > e2.time)
                    return 1;
                
                return 0;
            }
        });
        
    }
    
}

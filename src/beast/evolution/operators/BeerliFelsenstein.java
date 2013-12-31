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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    
    private enum EventType {MIGRATION, COALESCENCE, SAMPLE};
    private class Event {
        EventType eventType;
        double time;
        private EventType type;
        Node node;
        int thisDeme, prevDeme;
    }

    public BeerliFelsenstein() { }

    @Override
    public void initAndValidate() throws Exception {
        migModel = migModelInput.get();
        mtTree = multiTypeTreeInput.get();
    }
    
    @Override
    public double proposal() {
        double logHR = 0.0;
        
        // Select non-root node at random
        Node node = mtTree.getNode(Randomizer.nextInt(mtTree.getNodeCount())-1);
        MultiTypeNode mtNode = (MultiTypeNode)node;
        
        // Assemble partial event list
        List<Event> eventList = getPartialEventList(node);
        
        List<Set<Node>> nodesOfType = Lists.newArrayList();
        for (int i=0; i<migModel.getNDemes(); i++)
            nodesOfType.add(new HashSet<Node>());

        int deme = mtNode.getNodeType();
        for (Event event : eventList) {
            switch (event.type) {
                case COALESCENCE:
                    nodesOfType.get(event.thisDeme).removeAll(event.node.getChildren());
                    nodesOfType.get(event.thisDeme).add(event.node);
                    break;
                    
                case SAMPLE:
                    nodesOfType.get(event.thisDeme).add(event.node);
                    break;
                    
                case MIGRATION:
                    nodesOfType.get(event.prevDeme).remove(event.node);
                    nodesOfType.get(event.thisDeme).add(event.node);
                    break;
            }
            
            if (node.getHeight()<event.time)
                continue;

        }
     
        return logHR;
    }

    /**
     * Assemble and return list of events excluding those on the edge between
     * node and its parent.
     * 
     * @param excludedNode Tree node indicating edge to exclude.
     * @return event list
     */
    private List<Event> getPartialEventList(Node excludedNode) {
        List<Event> eventList = Lists.newArrayList();
        
        
        // Collect all events
        for (Node node : mtTree.getNodesAsArray()) {
            
            if (node == excludedNode)
                continue;

            MultiTypeNode mtNode = (MultiTypeNode)node;
            
            Event event = new Event();
            event.time = node.getHeight();
            event.node = node;
            event.thisDeme = mtNode.getNodeType();
            if (node.isLeaf())
                event.type = EventType.SAMPLE;
            else {
                if (!node.getChildren().contains(excludedNode))
                    event.type = EventType.COALESCENCE;
            }
            eventList.add(event);
            
            int thisDeme = mtNode.getNodeType();
            int prevDeme;
            for (int i=0; i<mtNode.getChangeCount(); i++) {
                prevDeme = thisDeme;
                thisDeme = mtNode.getChangeType(i);
                
                Event changeEvent = new Event();
                changeEvent.type = EventType.MIGRATION;
                changeEvent.time = mtNode.getChangeTime(i);
                changeEvent.node = node;
                changeEvent.thisDeme = thisDeme;
                changeEvent.prevDeme = prevDeme;
                eventList.add(changeEvent);
            }
            
        }
        
        // Sort events according to times
        Collections.sort(eventList, new Comparator<Event>() {

            @Override
            public int compare(Event e1, Event e2) {
                if (e1.time < e2.time)
                    return -1;
                
                if (e1.time > e2.time)
                    return 1;
                
                return 0;
            }
        });

        return eventList;
    }
}

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
        double oldRootHeight = mtTree.getRoot().getHeight();
        
        // Topology changes to turn tree into partial tree
        Node coalNode = node.getParent();
        if (!coalNode.isRoot())
            disconnectBranch(node);
        else {
            Node sister = getOtherChild(coalNode, node);
            coalNode.removeChild(sister);
        }
        
        // Pre-calculate total lineage migration propensities
        double [] migProp = new double[migModel.getNDemes()];
        for (int d=0; d<migModel.getNDemes(); d++) {
            migProp[d] = 0.0;
            for (int dp=0; dp<migModel.getNDemes(); dp++) {
                if (d==dp)
                    continue;
                migProp[d] += migModel.getRate(d, dp);
            }
        }
        
        List<Set<Node>> nodesOfType = Lists.newArrayList();
        for (int i=0; i<migModel.getNDemes(); i++)
            nodesOfType.add(new HashSet<Node>());

        mtNode.clearChanges();

        double coalTime = Double.NaN;
        for (int eidx=0; eidx<eventList.size(); eidx++) {
            Event event = eventList.get(eidx);
            double intervalEndTime;
            if (eidx<eventList.size()-1)
                intervalEndTime = eventList.get(eidx+1).time;
            else
                intervalEndTime = oldRootHeight;
            
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
            
            double t = Math.max(event.time,node.getHeight());
            
            // Early exit 
            if (t >= intervalEndTime)
                continue;

            int deme = mtNode.getNodeType();
            
            // Calculate coalescent propensity
            double coalProp = nodesOfType.get(deme).size()/migModel.getPopSize(deme);

            while (true) {
                
                // Select event time
                t += Randomizer.nextExponential(coalProp + migProp[deme]);
                if (t > intervalEndTime)
                    break;
            
                double u = Randomizer.nextDouble()*(coalProp + migProp[deme]);
                if (u<coalProp) {
                    // Coalescence
                
                    coalTime = t;
                    break;
                
                } else {
                    // Migration
                
                    u -= coalProp;
                    int toDeme;
                    for (toDeme = 0; toDeme<migModel.getNDemes(); toDeme++) {
                        if (toDeme == deme)
                            continue;
                    
                        u -= migModel.getRate(deme, toDeme);
                        if (u<0)
                            break;
                    }
                
                    mtNode.addChange(toDeme, t);
                    deme = toDeme;
                }
            }
            
            // Continue to next interval if no coalescence has occurred
            if (!Double.isNaN(coalTime))
                break;
        }
     
        if (Double.isNaN(coalTime)) {
            
            // Continue simulation beyond old root of tree
            double t = oldRootHeight;
            
            int deme = mtNode.getFinalType();
            MultiTypeNode mtNodeSis = (MultiTypeNode)eventList.get(eventList.size()-1).node;
            int demeSis = mtNodeSis.getFinalType();
            
            while (true) {
                
                // Calculate coalescent propensity
                double coalProp;
                if (deme == demeSis)
                    coalProp = 1.0/migModel.getPopSize(deme);
                else
                    coalProp = 0.0;
                
                double totalProp = coalProp + migProp[deme] + migProp[demeSis];
                t += Randomizer.nextExponential(totalProp);
                
                double u = Randomizer.nextDouble()*totalProp;
                
                if (u <coalProp) {
                    // Coalescence
                    
                    coalTime = t;
                    break;
                    
                } else {
                    // Migration
                    
                    u -= coalProp;
                    
                    if (u<migProp[deme]) {
                        // Migration in main lineage
                        
                        int toDeme;
                        for (toDeme=0; toDeme<migModel.getNDemes(); toDeme++) {
                            if (toDeme == deme)
                                continue;
                            
                            u -= migModel.getRate(deme, toDeme);
                            if (u<0)
                                break;
                        }
                        
                        mtNode.addChange(toDeme, t);
                        deme = toDeme;
                    } else {
                        // Migration in sister lineage
                        
                        int toDeme;
                        for (toDeme=0; toDeme<migModel.getNDemes(); toDeme++) {
                            if (toDeme == demeSis)
                                continue;
                            
                            u -= migModel.getRate(demeSis, toDeme);
                            if (u<0)
                                break;
                        }
                        
                        mtNodeSis.addChange(toDeme, t);
                        demeSis = toDeme;
                    }
                }
            }
        }
        
        // TODO: Implement coalescence
        
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

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

package multitypetree.operators;

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
        
        // Keep copies of node and its sister for reverse move prob calculation
        MultiTypeNode mtNodeOld = mtNode.shallowCopy();
        MultiTypeNode mtNodeOldSis = (MultiTypeNode)getOtherChild(node.getParent(), node);

        // Assemble partial event list
        List<Event> eventList = getPartialEventList(node);
        double oldRootHeight = mtTree.getRoot().getHeight();
        
        // Topology changes to turn tree into partial tree
        Node nodeParent = node.getParent();
        if (!nodeParent.isRoot())
            disconnectBranch(node);
        else {
            Node sister = getOtherChild(nodeParent, node);
            nodeParent.removeChild(sister);
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
            
            double t = Math.max(event.time, node.getHeight());
            
            // Early exit 
            if (t >= intervalEndTime)
                continue;

            int deme = mtNode.getNodeType();
            
            while (true) {
                
                // Calculate coalescent propensity
                double coalProp = nodesOfType.get(deme).size()/migModel.getPopSize(deme);

                // Select event time
                double dt = Randomizer.nextExponential(coalProp + migProp[deme]);
                t += dt;
                if (t > intervalEndTime)
                    break;
            
                // HR waiting time contribution
                logHR += -(coalProp + migProp[deme])*dt;
                
                double u = Randomizer.nextDouble()*(coalProp + migProp[deme]);
                if (u<coalProp) {
                    // Coalescence

                    // Select edge to coalesce with
                    Node coalNode = (Node)selectRandomElement(nodesOfType.get(deme));
                    
                    // HR event contribution
                    logHR += Math.log(1.0/migModel.getPopSize(deme));
                    
                    // Implement coalescence
                    coalTime = t;                    
                    connectBranch(node, coalNode, coalTime);

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
                
                    // HR event contribution
                    logHR += Math.log(migModel.getRate(deme, toDeme));

                    // Implelent migration
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
                double dt = Randomizer.nextExponential(totalProp);
                
                // HR waiting time contribution
                logHR += -totalProp*dt;
                
                t += dt;
                
                double u = Randomizer.nextDouble()*totalProp;
                
                if (u <coalProp) {
                    // Coalescence
                    
                    logHR += Math.log(1.0/migModel.getPopSize(deme));
                    
                    coalTime = t;
                    nodeParent.addChild(mtNodeSis);
                    mtTree.setRoot(nodeParent);
                    
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
                        
                        // HR contribution
                        logHR += Math.log(migModel.getRate(deme, toDeme));
                        
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
                        
                        // HR contribution
                        logHR += Math.log(migModel.getRate(demeSis, toDeme));
                        
                        mtNodeSis.addChange(toDeme, t);
                        demeSis = toDeme;
                    }
                }
            }
        }
        
        // TODO: Calculate Hastings factor
        
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
    
    /**
     * Calculate probability with which the current state is proposed from
     * the new state.
     * @param eventList List of events on partial genealogy
     * @param migProp Pre-calculated Migration propensities
     * @param node Node below edge which is modified during proposal
     * @param nodeSis Sister of node selected for proposal
     * @param oldCoalTime Original coalescence time of selected edge
     * @param newRootHeight Height of tree following proposal
     * @return log of proposal density
     */
    private double getReverseMoveProb(List<Event> eventList, double [] migProp,
            MultiTypeNode node, MultiTypeNode nodeSis,
            double oldCoalTime, double newRootHeight) {
        double logP = 0.0;
        
        MultiTypeNode mtNode = (MultiTypeNode)node;

        // Number of lineages in each deme - needed for coalescence propensity
        int[] lineageCounts = new int[migModel.getNDemes()];

        // Next change index
        int changeIdx = 0;
        
        // Current deme
        int deme = mtNode.getNodeType();
        
        // Flag to indicate this interval will terminate early due to
        // the start of a two-lineage simulation proposal phase
        boolean switchPhase = false;
        
        for (int eidx=0; (eidx<eventList.size()-1 && !switchPhase); eidx++) {
            Event event = eventList.get(eidx);
            double intervalEndTime = eventList.get(eidx+1).time;
            
            if (intervalEndTime>newRootHeight) {
                intervalEndTime = newRootHeight;
                switchPhase = true;
            }
            
            switch (event.type) {
                case COALESCENCE:
                    lineageCounts[event.thisDeme] -= 1;
                    break;
                    
                case SAMPLE:
                    lineageCounts[event.thisDeme] += 1;
                    break;
                    
                case MIGRATION:
                    lineageCounts[event.prevDeme] -= 1;
                    lineageCounts[event.thisDeme] += 1;
                    break;
            }

            if (node.getHeight()>intervalEndTime)
                continue;
            
            double t = Math.max(event.time, node.getHeight());

            
            // Loop over changes within this interval
            while (true) {
                
                // Calculate coalescence propensities
                double coalProp = lineageCounts[deme]/migModel.getPopSize(deme);
                
                double nextTime;
                if (changeIdx<mtNode.getChangeCount())
                    nextTime = mtNode.getChangeTime(eidx);
                else
                    nextTime = oldCoalTime;
                
                double dt = Math.min(nextTime, intervalEndTime) - t;
                
                logP += -(coalProp + migProp[deme])*dt;
                
                t += dt;
                if (t>intervalEndTime)
                    break;
                
                if (changeIdx<mtNode.getChangeCount()) {
                    // Migration
                    int toDeme = mtNode.getChangeType(changeIdx);
                    logP += Math.log(migModel.getRate(deme, toDeme));
                    deme = toDeme;
                } else {
                    // Coalescence
                    logP += Math.log(1.0/migModel.getPopSize(deme));
                    return logP;
                }
            }
        }
        
        double t = newRootHeight;
        
        int sisChangeIdx=0;
        int demeSis = nodeSis.getNodeType();
        while (sisChangeIdx<nodeSis.getChangeCount()
                && nodeSis.getChangeTime(sisChangeIdx)<newRootHeight) {
            demeSis = nodeSis.getChangeType(sisChangeIdx);
            sisChangeIdx += 1;
        }
        
        // TODO: Finish calculation
        
        while (true) {
            // Calculate propensities
            double coalProp;
            if (deme == demeSis)
                coalProp = 1.0/migModel.getPopSize(deme);
            else
                coalProp = 0.0;
        }
        
//        return logP;
    }

    /**
     * Select element at random from set.
     * 
     * @param set
     * @return Object
     */
    public Object selectRandomElement(Set set) {
        return set.toArray()[Randomizer.nextInt(set.size())];
    }
}
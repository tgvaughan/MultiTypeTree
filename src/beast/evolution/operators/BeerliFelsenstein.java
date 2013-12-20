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
        
        /**
         * Type during interval following event.
         */
        int thisType;
        
        /**
         * Type during previous interval if migration event.
         */
        int prevType;
        
        /**
         * Number of lineages possessing each type during this interval.
         */
        int [] k;
        
        /**
         * Nodes with parent edges which overlap this interval.
         */
        List<Node> activeNodes;

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
                
        double logHR = 0.0;
        
        // Select non-root node at random:
        MultiTypeNode node;
        do {
            node = (MultiTypeNode)mtTree.getNode(Randomizer.nextInt(mtTree.getNodeCount()));
        } while (node.isRoot());
        
        // Store original node (needed for reverse move density calculation)
        Node originalNode = node.shallowCopy();
        
        // Assemble list of events on partial tree
        assemblePartialEventList(node);
        
        // Actually turn tree into partial tree.
        node.clearChanges();
        MultiTypeNode mtParent = (MultiTypeNode)node.getParent();
        MultiTypeNode mtSister = (MultiTypeNode)getOtherChild(mtParent, node);
        mtParent.removeChild(mtSister);
        
        if (!mtParent.isRoot()) {
            for (int i=0; i<mtParent.getChangeCount(); i++) {
                mtSister.addChange(mtParent.getChangeType(i), mtParent.getChangeTime(i));
            }
            mtParent.clearChanges();
            MultiTypeNode mtGrandParent = (MultiTypeNode)mtParent.getParent();
            mtGrandParent.removeChild(mtParent);
            mtGrandParent.addChild(mtSister);
        }
        
        // Simulate new edge(s)
        
        int [] migPropTots = new int[migModel.getNDemes()];
        for (int d=0; d<migModel.getNDemes(); d++) {
            for (int dp=0; dp<migModel.getNDemes(); dp++) {
                if (dp==d)
                    continue;
                migPropTots[d] += migModel.getRate(d, dp);
            }

        }
        double t = node.getHeight();        
        int thisType = node.getFinalType();
        for (int eidx=1; eidx<eventList.size(); eidx++) {
            TreeEvent nextEvent = eventList.get(eidx);
            if (nextEvent.time<t)
                continue;
            
            TreeEvent event = eventList.get(eidx-1);

            double a0 = migPropTots[node.getFinalType()]
                    + event.k[thisType]/migModel.getPopSize(thisType);
            
            t += Randomizer.nextExponential(a0);
            
            if (t>nextEvent.time) {
                t = nextEvent.time;
                continue;
            }
            
            double u = Randomizer.nextDouble()*a0;
            if (u<migPropTots[thisType]) {
                
                // New type change
                int nextType;
                for (nextType=0; nextType<migModel.getNDemes(); nextType++) {
                    if (nextType==thisType)
                        continue;
                    
                    u -= migModel.getRate(thisType, nextType);
                    if (u<0)
                        break;
                }
                
                node.addChange(nextType, t);
                thisType = nextType;
                
            } else {
                
                // Coalesce!
                
                int idx = Randomizer.nextInt(event.k[thisType]);
                for (Node cNode : event.activeNodes) {

                }
                
                break;
            }
        }

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
     * Assemble list of events in tree, excluding those on the edge between
     * nodeToExclude and its parent.
     * 
     * @param nodeToExclude node to exclude from partial event list.
     */
    private void assemblePartialEventList(Node nodeToExclude) {
        
        eventList.clear();
        
        for (Node node : mtTree.getNodesAsArray()) {
            if (node == nodeToExclude)
                continue;
            
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
        
        // Calculate lineage counts:
        int [] k = new int[mtTree.getNTypes()];
        List<Node> activeNodes = Lists.newArrayList();
        for (TreeEvent event : eventList) {
            switch (event.eventType) {
                case COALESCENCE:
                    k[event.thisType] -= 1;
                    activeNodes.remove(event.node.getLeft());
                    activeNodes.remove(event.node.getRight());
                    activeNodes.add(event.node);
                    break;
                case MIGRATION:
                    k[event.thisType] += 1;
                    k[event.prevType] -= 1;
                    break;
                case SAMPLE:
                    k[event.thisType] += 1;
                    activeNodes.add(event.node);
                    break;
            }
            event.k = k.clone();
            event.activeNodes = Lists.newArrayList(activeNodes);
        }
    }
    
}

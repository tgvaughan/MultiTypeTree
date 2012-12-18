package beast.evolution.tree;

import beast.core.*;
import beast.util.Randomizer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author dkuh004
 *         Date: Jul 16, 2012
 *         Time: 4:22:40 PM
 */
@Description("Class to initialize a ColouredTree from random tree by adding minimum number of changes needed")
public class RandomColouredTree extends ColouredTree implements StateNodeInitialiser {

    public Input<TraitSet> m_trait = new Input<TraitSet>("trait",
            "trait information for initializing leaf node colours");

    @Override
    public void initAndValidate() throws Exception {

        super.initAndValidate();
        
        // Hack to deal with StateNodes that haven't been attached to
        // a state yet. (Only necessary when using method to simulate
        // a whole bunch of trees rather than initilize and MCMC chain.)
        if (changeColours.getState() == null) {
            State state = new State();
            state.initByName(
                    "stateNode", changeColours,
                    "stateNode", changeTimes,
                    "stateNode", changeCounts,
                    "stateNode", nodeColours);
            state.initialise();
        }

        initStateNodes() ;
    }


    @Override
    public void initStateNodes()  throws Exception {
        
        initParameters(tree.getLeafNodeCount()*2 + 1);
                
        /* fill leaf colour array */
        for (int i = 0; i<tree.getLeafNodeCount(); i++)
            getModifier().setNodeColour(tree.getNode(i), (int)m_trait.get().getValue(i));

        simulateColouring(tree.getRoot());
        
        if (!isValid())
            throw new Exception("Inconsistent colour assignment.");
        
    }

    /**
     * Minimal random colouring of tree consistent with leaf colours.
     * 
     * @param node 
     */
    public void simulateColouring(Node node){

        if (node.getNodeCount() >= 3){
            Node left = node.getChild(0);
            Node right = node.getChild(1);

            if (left.getNodeCount() >= 3)  simulateColouring(left);
            if (right.getNodeCount() >= 3)  simulateColouring(right);
            
            int leftCol = getFinalBranchColour(left);
            int rightCol = getFinalBranchColour(right);

            if (leftCol == rightCol)
                getModifier().setNodeColour(node, leftCol);

            else {

                Node nodeToKeep, other;
                if (Randomizer.nextBoolean()) {
                    nodeToKeep = left;
                    other = right;
                } else {
                    nodeToKeep = right;
                    other = left;
                }

                getModifier().setNodeColour(node, getNodeColour(nodeToKeep));

                double changeTime = Randomizer.nextDouble()*(node.getHeight()-other.getHeight()) + other.getHeight();
                getModifier().addChange(other, getNodeColour(node), changeTime);
            }
        }
    }

    @Override
    public List<StateNode> getInitialisedStateNodes() {

        List<StateNode> statenodes = new ArrayList<StateNode>();

        statenodes.add(changeColoursInput.get());
        statenodes.add(changeTimesInput.get());
        statenodes.add(changeCountsInput.get());
        statenodes.add(nodeColoursInput.get());

        return statenodes;

    }
}

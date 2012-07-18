package beast.evolution.tree;

import beast.core.*;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.util.Randomizer;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author dkuh004
 *         Date: Jul 16, 2012
 *         Time: 4:22:40 PM
 */
@Description("Class to initialize a ColouredTree from random tree by adding minimum number of changes needed")
public class RandomColouredTree extends ColouredTree implements StateNodeInitialiser {

    public Input<TraitSet> m_trait = new Input<TraitSet>("trait", "trait information for initializing traits (like node dates) in the tree");


    Integer[] nodeCols;
    Integer[] changeCols;
    Double[] times;
    Integer[]  counts;

    public void initAndValidate() throws Exception{

        super.initAndValidate();

        int nNodes = tree.getNodeCount();

        nodeCols = new Integer[nNodes];
        changeCols = new Integer[nNodes*maxBranchColours];
        times = new Double[nNodes*maxBranchColours];
        counts =  new Integer[nNodes]; Arrays.fill(counts,0);

        initStateNodes() ;
    }


    public void initStateNodes()  throws Exception {

        simulateColouring();

        nodeColoursInput.get().assignFromWithoutID(new IntegerParameter(nodeCols));
        changeColoursInput.get().assignFromWithoutID(new IntegerParameter(changeCols));
        changeTimesInput.get().assignFromWithoutID(new RealParameter(times));
        changeCountsInput.get().assignFromWithoutID(new IntegerParameter(counts));

    }

    public void simulateColouring(){

        /* fill leaf colour array */
        for (int i = 0; i<tree.getLeafNodeCount(); i++){

            nodeCols[i] = (int) m_trait.get().getValue( tree.getNode(i).getNr() ) ;

        }

        simulateColouring(tree.getRoot());

    }

    public void simulateColouring(Node node){

        if (node.getNodeCount() >= 3){
            Node left = node.getChild(0);
            Node right = node.getChild(1);

            if (left.getNodeCount() >= 3)  simulateColouring(left);
            if (right.getNodeCount() >= 3)  simulateColouring(right);

            int leftCol = nodeCols[left.getNr()];
            int rightCol = nodeCols[right.getNr()];


            if (leftCol == rightCol) nodeCols[node.getNr()] = leftCol;

            else {

                int keep = Randomizer.nextBoolean()? 1 : 0;

                nodeCols[node.getNr()] = nodeCols[node.getChild(keep).getNr()];

                Node changeChild = node.getChild(1-keep);
                double time = (node.getHeight() - changeChild.getHeight())*Randomizer.nextDouble() + changeChild.getHeight();

                addChange(changeChild.getNr(), counts[changeChild.getNr()], nodeCols[node.getNr()], time, changeCols, times, counts);
            }
        }
    }



    public List<StateNode> getInitialisedStateNodes() {

        List<StateNode> statenodes = new ArrayList<StateNode>();

        statenodes.add(changeColoursInput.get());
        statenodes.add(changeTimesInput.get());
        statenodes.add(changeCountsInput.get());
        statenodes.add(nodeColoursInput.get());

        return statenodes;

    }
}

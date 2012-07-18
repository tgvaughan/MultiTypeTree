package beast.evolution.tree;

import beast.core.*;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.util.Randomizer;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

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

        System.out.println("in RandomColouredtree");

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

        if (!isValid())
            throw new Exception("Inconsistent colour assignment.");
        
    }

    public void simulateColouring() throws Exception {

        /* fill leaf colour array */
        for (int i = 0; i<tree.getLeafNodeCount(); i++){

            nodeCols[i] = (int) m_trait.get().getValue(i) ;

        }

        simulateColouring(tree.getRoot());

    }

    public void simulateColouring(Node node){

        if (node.getNodeCount() >= 3){
            Node left = node.getChild(0);
            Node right = node.getChild(1);

            if (left.getNodeCount() >= 3)  simulateColouring(left);
            if (right.getNodeCount() >= 3)  simulateColouring(right);

            int leftCol = counts[left.getNr()]==0 ? nodeCols[left.getNr()] : changeCols[left.getNr()*maxBranchColours + counts[left.getNr()] - 1];
            int rightCol = counts[right.getNr()]==0 ? nodeCols[right.getNr()] : changeCols[right.getNr()*maxBranchColours + counts[right.getNr()] - 1];


            if (leftCol == rightCol)
                changeNodeColour(node.getNr(), leftCol);

            else {

                int keep = Randomizer.nextBoolean()? 1 : 0;

                if (nodeCols[node.getNr()]!=null) {
                    if (nodeCols[node.getNr()] == leftCol) keep = 0;
                    if (nodeCols[node.getNr()] == rightCol) keep = 1;
                }

                changeNodeColour(node.getNr(), nodeCols[node.getChild(keep).getNr()]);

                Node changeChild = node.getChild(1-keep);
                double lowerBound = counts[changeChild.getNr()]==0 ? changeChild.getHeight() : times[changeChild.getNr()*maxBranchColours + counts[changeChild.getNr()] - 1];
                double time = (node.getHeight() - lowerBound)*Randomizer.nextDouble() + lowerBound;

                addChange(changeChild.getNr(), changeChild.getNr()*maxBranchColours + counts[changeChild.getNr()], nodeCols[node.getNr()], time, changeCols, times, counts);
            }
        }
    }

    public void changeNodeColour(int nodeNr, int colour){

        if (!(nodeCols[nodeNr] == null ||  nodeCols[nodeNr] == colour || tree.getNode(nodeNr).isRoot())){  //nodeCols[nodeNr] = colour;

      //  else {

            double time;

            if (counts[nodeNr] ==0) {

                time = (tree.getNode(nodeNr).getParent().getHeight() - tree.getNode(nodeNr).getHeight())*Randomizer.nextDouble() + tree.getNode(nodeNr).getHeight();

                addChange(nodeNr, nodeNr*maxBranchColours + counts[nodeNr], nodeCols[nodeNr], time, changeCols, times, counts);
            }

            else { // need to insert a change before all others

                Node node = tree.getNode(nodeNr);

                if (!(nodeCols[node.getParent().getNr()] == colour)){

                    for (int i = counts[nodeNr]; i > 0; i--){
                        times[nodeNr*maxBranchColours + i] =  times[nodeNr*maxBranchColours + i -1];
                        changeCols[nodeNr*maxBranchColours + i] =  changeCols[nodeNr*maxBranchColours + i -1];
                    }

                    time = (times[nodeNr*maxBranchColours + 1] - node.getHeight())*Randomizer.nextDouble() + node.getHeight();

                    addChange(nodeNr, nodeNr*maxBranchColours, nodeCols[nodeNr], time, changeCols, times, counts);
                }
                else counts[nodeNr] = 0;
            }
        }

        nodeCols[nodeNr] = colour;

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

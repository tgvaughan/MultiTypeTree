package beast.evolution.tree;

import beast.core.*;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.util.TreeParser;

import java.util.List;
import java.util.ArrayList;

/**
 * @author dkuh004
 *         Date: Jun 18, 2012
 *         Time: 3:03:07 PM
 */

@Description("Class to initialize a ColouredTree from single child newick tree with colour metadata")
public class ColouredTreeFromNewick extends ColouredTree implements StateNodeInitialiser {


    public Input<TreeParser> colouredNewick = new Input<TreeParser>(
            "colouredNewick", "Newick tree with single children and meta data containing colouring.");



    protected ColouredTree ctree;

    public void initAndValidate() throws Exception{

        ctree = new ColouredTree(treeInput.get(), colouredNewick.get(), colourLabelInput.get(), nColoursInput.get(),maxBranchColoursInput.get());

        changeColours = changeColoursInput.get();
        changeTimes = changeTimesInput.get();
        changeCounts = changeCountsInput.get();
        nodeColours = nodeColoursInput.get(); 
        tree= treeInput.get(); //ctree.tree;

        colourLabel = colourLabelInput.get();
        nColours = nColoursInput.get();
        maxBranchColours = maxBranchColoursInput.get();

        initStateNodes() ;
    }


    public void initStateNodes()  {

        changeColoursInput.get().assignFromWithoutID(ctree.changeColours);
        changeTimesInput.get().assignFromWithoutID(ctree.changeTimes);
        changeCountsInput.get().assignFromWithoutID(ctree.changeCounts);
        nodeColoursInput.get().assignFromWithoutID(ctree.nodeColours);
        treeInput.get().assignFromWithoutID(ctree.tree);     
        System.out.println();
    }

    public List<StateNode> getInitialisedStateNodes() {

        List<StateNode> statenodes = new ArrayList<StateNode>();

        statenodes.add(treeInput.get());
        statenodes.add(changeColoursInput.get());
        statenodes.add(changeTimesInput.get());
        statenodes.add(changeCountsInput.get());
        statenodes.add(nodeColoursInput.get());

        return statenodes;

    }
}

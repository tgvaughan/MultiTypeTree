package beast.evolution.tree;

import beast.core.*;
import beast.core.Input.Validate;
import beast.util.TreeParser;
import java.util.ArrayList;
import java.util.List;

/**
 * @author dkuh004
 *         Date: Jun 18, 2012
 *         Time: 3:03:07 PM
 */

@Description("Class to initialize a ColouredTree from single child newick tree with colour metadata")
public class ColouredTreeFromNewick extends ColouredTree implements StateNodeInitialiser {

    public Input<String> newickStringInput = new Input<String>("newick",
            "Tree in Newick format.", Validate.REQUIRED);

    @Override
    public void initAndValidate() throws Exception {
        
        super.initAndValidate();
        
        TreeParser parser = new TreeParser();
        parser.initByName(
                "IsLabelledNewick", true,
                "adjustTipHeights", true,
                "singlechild", true,
                "newick", newickStringInput.get());
        Tree flatTree = parser;
        
        initFromFlatTree(flatTree);
        initStateNodes();
    }


    @Override
    public void initStateNodes()  { }

    @Override
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

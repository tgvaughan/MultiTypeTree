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

@Description("Class to initialize a MultiTypeTree from single child newick tree with type metadata")
public class MultiTypeTreeFromNewick extends MultiTypeTree implements StateNodeInitialiser {

    public Input<String> newickStringInput = new Input<>("newick",
            "Tree in Newick format.", Validate.REQUIRED);

    public Input<Boolean> adjustTipHeightsInput = new Input<>("adjustTipHeights",
            "Adjust tip heights in tree? Default true.", true);

    @Override
    public void initAndValidate() throws Exception {
        
        super.initAndValidate();
        
        TreeParser parser = new TreeParser();
        parser.initByName(
                "IsLabelledNewick", true,
                "adjustTipHeights", adjustTipHeightsInput.get(),
                "singlechild", true,
                "newick", newickStringInput.get());

        initFromFlatTree(parser, true);
    }

    @Override
    public void initStateNodes() { }

    @Override
    public void getInitialisedStateNodes(List<StateNode> stateNodeList) {
        stateNodeList.add(this);
    }
}

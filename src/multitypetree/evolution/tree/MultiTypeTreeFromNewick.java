package multitypetree.evolution.tree;



import java.util.ArrayList;
import java.util.List;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.evolution.tree.TreeParser;
import beast.base.inference.StateNode;
import beast.base.inference.StateNodeInitialiser;

/**
 * @author dkuh004
 *         Date: Jun 18, 2012
 *         Time: 3:03:07 PM
 */

@Description("Class to initialize a MultiTypeTree from single child newick tree with type metadata")
public class MultiTypeTreeFromNewick extends MultiTypeTree implements StateNodeInitialiser {

    public Input<String> newickStringInput = new Input<>("value",
            "Tree in Newick format.", Validate.REQUIRED);

    public Input<Boolean> adjustTipHeightsInput = new Input<>("adjustTipHeights",
            "Adjust tip heights in tree? Default false.", false);

    @Override
    public void initAndValidate() {
        
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
	public void getInitialisedStateNodes(List<StateNode> stateNodes) {
		stateNodes.add(this);
	}
}

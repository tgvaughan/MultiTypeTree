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

    public Input<String> newickStringInput = new Input<String>("newick",
            "Tree in Newick format.", Validate.REQUIRED);

    @Override
    public void initAndValidate() throws Exception {
        
        super.initAndValidate();

        if ((timeTraitSetInput.get() != null) || (typeTraitSetInput.get() != null))
            System.err.println("Warning: time and type traits will be ignored "
                    + "in favour of Newick specification.");
        
        TreeParser parser = new TreeParser();
        parser.initByName(
                "IsLabelledNewick", true,
                "adjustTipHeights", true,
                "singlechild", true,
                "newick", newickStringInput.get());
        Tree flatTree = parser;
        
        initFromFlatTree(flatTree);
    }

    @Override
    public void initStateNodes() { }

    @Override
    public List<StateNode> getInitialisedStateNodes() {
        List<StateNode> stateNodeList = new ArrayList<StateNode>();
        stateNodeList.add(this);
        
        return stateNodeList;
    }
}

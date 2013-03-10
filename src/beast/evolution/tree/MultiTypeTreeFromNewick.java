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
public class MultiTypeTreeFromNewick extends MultiTypeTree {

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
    }
}

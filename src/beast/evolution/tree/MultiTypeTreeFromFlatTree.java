package beast.evolution.tree;

import beast.core.Description;
import beast.core.Input;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Class to initialise a MultiTypeTree from a " +
        "BEAST tree in which single-child nodes represent " +
        "type changes.")
public class MultiTypeTreeFromFlatTree extends MultiTypeTree {

    public Input<Tree> flatTreeInput = new Input<>(
            "flatTree",
            "Flat representation of multi-type tree.",
            Input.Validate.REQUIRED);

    @Override
    public void initAndValidate() throws Exception {
        super.initAndValidate();

        initFromFlatTree(flatTreeInput.get(), true);
    }
}

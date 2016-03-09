package multitypetree.operators;

import beast.core.Input;
import beast.core.Operator;
import beast.core.parameter.IntegerParameter;
import beast.evolution.tree.MigrationModel;
import beast.evolution.tree.Tree;
import beast.util.Randomizer;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class NodeTypeParamOperator extends Operator {

    public Input<IntegerParameter> nodeTypesInput = new Input<>(
            "nodeTypes",
            "Node types parameter on which to operate.",
            Input.Validate.REQUIRED);

    public Input<MigrationModel> migrationModelInput = new Input<>(
            "migrationModel",
            "Migration model.",
            Input.Validate.REQUIRED);

    public Input<Tree> treeInput = new Input<>(
            "tree",
            "Tree to which node types correspond.",
            Input.Validate.REQUIRED);

    IntegerParameter nodeTypes;
    MigrationModel model;
    Tree tree;

    @Override
    public void initAndValidate() {
        nodeTypes = nodeTypesInput.get();
        model = migrationModelInput.get();
        tree = treeInput.get();
    }

    @Override
    public double proposal() {

        nodeTypes.setValue(tree.getLeafNodeCount() +
                Randomizer.nextInt(tree.getInternalNodeCount()),
                Randomizer.nextInt(model.getNTypes()));

        return 0;
    }

}

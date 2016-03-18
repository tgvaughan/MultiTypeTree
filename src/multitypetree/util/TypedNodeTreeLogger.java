package multitypetree.util;

import beast.core.BEASTObject;
import beast.core.Description;
import beast.core.Input;
import beast.core.Loggable;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.MultiTypeTree;
import beast.evolution.tree.Node;

import java.io.PrintStream;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class TypedNodeTreeLogger extends BEASTObject implements Loggable {

    public Input<MultiTypeTree> multiTypeTreeInput = new Input<>(
            "multiTypeTree",
            "Multi-type tree to log.",
            Input.Validate.REQUIRED);

    MultiTypeTree mtTree;

    @Override
    public void initAndValidate() {
        mtTree = multiTypeTreeInput.get();
    }

    @Override
    public void init(PrintStream out) {
        mtTree.init(out);
    }

    @Override
    public void log(int nSample, PrintStream out) {

        // Set up metadata string
        for (Node node : mtTree.getNodesAsArray()) {
            MultiTypeNode mtNode = (MultiTypeNode)node;
            mtNode.metaDataString = mtTree.getTypeLabel()
                    + "=\""
                    + mtTree.getTypeString(mtNode.getNodeType())
                    + "\"";
        }

        out.print("tree STATE_" + nSample + " = ");
        out.print(mtTree.getRoot().toSortedNewick(new int[1], true));
        out.print(";");
    }

    @Override
    public void close(PrintStream out) {
        mtTree.close(out);
    }
}

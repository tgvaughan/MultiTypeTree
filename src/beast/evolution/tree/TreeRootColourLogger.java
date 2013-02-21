package beast.evolution.tree;

/**
 * @author Denise Kuehnert
 *         Date: Feb 12, 2013
 *         Time: 2:09:02 PM
 */


import beast.core.CalculationNode;
import beast.core.Description;
import beast.core.Input;
import beast.core.Valuable;
import beast.core.Input.Validate;
import beast.core.Loggable;
import beast.core.parameter.IntegerParameter;

import java.io.PrintStream;

@Description("Logger to report height of a tree")
public class TreeRootColourLogger extends CalculationNode implements Loggable, Valuable {

    public Input<Tree> m_tree = new Input<Tree>("tree", "tree to report height for.", Validate.REQUIRED);
    public Input<IntegerParameter> nodeColoursInput = new Input<IntegerParameter>(
            "nodeColours", "Colour at each node (including internal nodes).");

    @Override
    public void initAndValidate() {
        // nothing to do
    }

    @Override
    public void init(PrintStream out) throws Exception {
        final Tree tree = m_tree.get();
        if (getID() == null || getID().matches("\\s*")) {
            out.print(tree.getID() + ".rootColor\t");
        } else {
            out.print(getID() + "\t");
        }
    }

    @Override
    public void log(int nSample, PrintStream out) {
        final Tree tree = m_tree.get();
        out.print(nodeColoursInput.get().getArrayValue(tree.getRoot().getNr()) + "\t");
    }

    @Override
    public void close(PrintStream out) {
        // nothing to do
    }

    @Override
    public int getDimension() {
        return 1;
    }

    @Override
    public double getArrayValue() {
        return nodeColoursInput.get().getArrayValue(m_tree.get().getRoot().getNr());
    }

    @Override
    public double getArrayValue(int iDim) {
        return nodeColoursInput.get().getArrayValue(m_tree.get().getRoot().getNr());
    }
}


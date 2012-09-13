package beast.evolution.operator;


import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.evolution.tree.ColouredTree;
import beast.util.Randomizer;


/**
 * @author Denise Kuehnert
 *         Date: Aug 30, 2012
 *         Time: 4:37:32 PM
 */

import beast.core.Description;

@Description("Randomly selects true internal tree node (i.e. not the root) and move node height uniformly in interval " +
        "restricted by the nodes parent and children.")
public class ColouredUniform extends ColouredTreeOperator {

    @Override
    public void initAndValidate() {
    }

    /**
     * change the nodeheight and return the hastings ratio.
     *
     * @return log of Hastings Ratio
     */
    @Override
    public double proposal() {
        final Tree tree = m_tree.get(this);
        ColouredTree ctree = colouredTreeInput.get();

        // randomly select internal node
        final int nNodeCount = tree.getNodeCount();
        Node node;
        do {
            final int iNodeNr = nNodeCount / 2 + 1 + Randomizer.nextInt(nNodeCount / 2);
            node = tree.getNode(iNodeNr);
        } while (node.isRoot() || node.isLeaf());

        // upper is first node colour change, if there is one, otherwise it's parent height
        final double fUpper = (ctree.getChangeCount(node) > 0)? (ctree.getChangeTime(node,0)) : node.getParent().getHeight();

        // lower is maximum of childrens' last node colour change, if there are any, otherwise of their heights         
        Node left = node.getLeft();
        Node right = node.getRight();
        int lCount = ctree.getChangeCount(left);
        int rCount = ctree.getChangeCount(right);
        final double fLower = Math.max((lCount>0? (ctree.getChangeTime(left, lCount-1)) :left.getHeight()), ( rCount>0? (ctree.getChangeTime(right, rCount-1)) : right.getHeight()));

        final double newValue = (Randomizer.nextDouble() * (fUpper - fLower)) + fLower;
        node.setHeight(newValue);

        return 0.0;
    }

}

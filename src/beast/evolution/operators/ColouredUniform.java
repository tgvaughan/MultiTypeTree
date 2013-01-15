package beast.evolution.operators;


import beast.core.Description;
import beast.evolution.tree.Node;
import beast.util.Randomizer;

@Description("Randomly selects true internal tree node (i.e. not the root) and"
        + " moves node height uniformly in interval restricted by the node's"
        + " parent and children.")
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

        cTree = colouredTreeInput.get();
        tree = cTree.getUncolouredTree();

        // randomly select internal node
        final int nNodeCount = tree.getNodeCount();
        Node node;
        do {
            final int iNodeNr = nNodeCount / 2 + 1 + Randomizer.nextInt(nNodeCount / 2);
            node = tree.getNode(iNodeNr);
        } while (node.isRoot() || node.isLeaf());

        // upper is first node colour change, if there is one, otherwise it's parent height
        final double fUpper = (cTree.getChangeCount(node) > 0)? (cTree.getChangeTime(node,0)) : node.getParent().getHeight();

        // lower is maximum of childrens' last node colour change, if there are any, otherwise of their heights         
        Node left = node.getLeft();
        Node right = node.getRight();
        int lCount = cTree.getChangeCount(left);
        int rCount = cTree.getChangeCount(right);
        final double fLower = Math.max((lCount>0? (cTree.getChangeTime(left, lCount-1)) :left.getHeight()), ( rCount>0? (cTree.getChangeTime(right, rCount-1)) : right.getHeight()));

        final double newValue = (Randomizer.nextDouble() * (fUpper - fLower)) + fLower;
        node.setHeight(newValue);

        return 0.0;
    }

}

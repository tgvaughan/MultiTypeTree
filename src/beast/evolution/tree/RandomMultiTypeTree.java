package beast.evolution.tree;

import beast.core.*;
import beast.util.Randomizer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author dkuh004
 *         Date: Jul 16, 2012
 *         Time: 4:22:40 PM
 * 
 * May not be very useful in its current form.  Need a full implementation of
 * RandomTree for multi-type trees.
 */
@Description("Class to initialize a MultiTypeTree from random tree by adding minimum number of changes needed")
public class RandomMultiTypeTree extends MultiTypeTree implements StateNodeInitialiser {

    @Override
    public void initAndValidate() {
        super.initAndValidate();
        
        if (!hasTypeTrait())
            throw new IllegalArgumentException("No trait set with name '" + typeLabel + "' "
                    + "identified.  Needed to specify taxon locations.");
        
        // Fill leaf colour array:
        for (int i = 0; i<getLeafNodeCount(); i++)
            ((MultiTypeNode)getNode(i)).setNodeType(
                    getTypeList().indexOf(typeTraitSet.getStringValue(i)));

        generateTyping(getRoot());
        
        if (!isValid())
            throw new IllegalStateException("Inconsistent colour assignment.");
        
    }


    @Override
    public void initStateNodes() { }

    /**
     * Minimal random colouring of tree consistent with leaf colours.
     * 
     * @param node 
     */
    public void generateTyping(Node node){

        if (node.getNodeCount() >= 3){
            Node left = node.getChild(0);
            Node right = node.getChild(1);

            if (left.getNodeCount() >= 3)  generateTyping(left);
            if (right.getNodeCount() >= 3)  generateTyping(right);
            
            int leftCol = ((MultiTypeNode)left).getFinalType();
            int rightCol = ((MultiTypeNode)right).getFinalType();

            if (leftCol == rightCol)
                ((MultiTypeNode)node).setNodeType(leftCol);

            else {

                Node nodeToKeep, other;
                if (Randomizer.nextBoolean()) {
                    nodeToKeep = left;
                    other = right;
                } else {
                    nodeToKeep = right;
                    other = left;
                }

                ((MultiTypeNode)node).setNodeType(((MultiTypeNode)nodeToKeep).getNodeType());

                double changeTime = Randomizer.nextDouble()*(node.getHeight()-other.getHeight()) + other.getHeight();
                ((MultiTypeNode)other).addChange(((MultiTypeNode)node).getNodeType(), changeTime);
            }
        }
    }

    @Override
    public void getInitialisedStateNodes(List<StateNode> stateNodeList) {
        stateNodeList.add(this);
    }
}

/*
 * Copyright (C) 2013 Tim Vaughan <tgvaughan@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package beast.evolution.tree;

import beast.core.Description;
import beast.core.Input;
import beast.core.StateNode;
import beast.core.StateNodeInitialiser;
import beast.util.TreeParser;
import com.google.common.collect.Lists;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("A multi-type phylogenetic tree.")
public class MultiTypeTree extends Tree {

    /*
     * Plugin inputs:
     */
    public Input<Integer> nTypesInput = new Input<Integer>(
            "nTypes",
            "Number of distinct types to consider.", Input.Validate.REQUIRED);
    
    public Input<String> typeLabelInput = new Input<String>(
            "typeLabel",
            "Label for type traits (e.g. deme)", "deme");
    
    /*
     * Non-input fields:
     */
    protected String typeLabel;
    protected int nTypes;

    public MultiTypeTree() { };
    
    public MultiTypeTree(Node rootNode) {
        
        if (!(rootNode instanceof MultiTypeNode)) {
            throw new IllegalArgumentException("Attempted to instantiate "
                    + "multi-type tree with regular root node.");
        }
        
        setRoot(rootNode);
        initArrays();
    }
    
    @Override
    public void initAndValidate() throws Exception {
        
        if (m_initial.get() != null && !(this instanceof StateNodeInitialiser)) {
            
            if (!(m_initial.get() instanceof MultiTypeTree)) {
                throw new IllegalArgumentException("Attempted to initialise "
                        + "multi-type tree with regular tree object.");
            }
            
            MultiTypeTree other = (MultiTypeTree)m_initial.get();
            root = other.root.copy();
            nodeCount = other.nodeCount;
            internalNodeCount = other.internalNodeCount;
            leafNodeCount = other.leafNodeCount;
        }

        if (nodeCount < 0) {
            if (m_taxonset.get() != null) {
                // make a caterpillar
                List<String> sTaxa = m_taxonset.get().asStringList();
                Node left = new MultiTypeNode();
                left.m_iLabel = 0;
                left.m_fHeight = 0;
                left.setID(sTaxa.get(0));
                for (int i = 1; i < sTaxa.size(); i++) {
                    Node right = new MultiTypeNode();
                    right.m_iLabel = i;
                    right.m_fHeight = 0;
                    right.setID(sTaxa.get(i));
                    Node parent = new MultiTypeNode();
                    parent.m_iLabel = sTaxa.size() + i - 1;
                    parent.m_fHeight = i;
                    left.m_Parent = parent;
                    parent.setLeft(left);
                    right.m_Parent = parent;
                    parent.setRight(right);
                    left = parent;
                }
                root = left;
                leafNodeCount = sTaxa.size();
                nodeCount = leafNodeCount * 2 - 1;
                internalNodeCount = leafNodeCount - 1;

            } else {
                // make dummy tree with a single root node
                root = new MultiTypeNode();
                root.m_iLabel = 0;
                root.m_fHeight = 0;
                root.m_tree = this;
                nodeCount = 1;
                internalNodeCount = 0;
                leafNodeCount = 1;
            }
        }
        if (m_trait.get() != null) {
            adjustTreeToNodeHeights(root, m_trait.get());
        }

        if (nodeCount >= 0) {
            initArrays();
        }
        
        typeLabel = typeLabelInput.get();
        nTypes = nTypesInput.get();
    }

    @Override
    protected final void initArrays() {
        // initialise tree-as-array representation + its stored variant
        m_nodes = new MultiTypeNode[nodeCount];
        listNodes((MultiTypeNode)root, (MultiTypeNode[])m_nodes);
        m_storedNodes = new MultiTypeNode[nodeCount];
        Node copy = root.copy();
        listNodes((MultiTypeNode)copy, (MultiTypeNode[])m_storedNodes);
    }

    /**
     * Convert multi-type tree to array representation.
     *
     * @param node Root of sub-tree to convert.
     * @param nodes Array to populate with tree nodes.
     */
    private void listNodes(MultiTypeNode node, MultiTypeNode[] nodes) {
        nodes[node.getNr()] = node;
        node.m_tree = this;
        if (!node.isLeaf()) {
            listNodes(node.getLeft(), nodes);
            if (node.getRight()!=null)
                listNodes(node.getRight(), nodes);
        }
    }

    /**
     * Deep copy, returns a completely new multi-type tree.
     *
     * @return a deep copy of this multi-type tree
     */
    @Override
    public MultiTypeTree copy() {
        MultiTypeTree tree = new MultiTypeTree();
        tree.m_sID = m_sID;
        tree.index = index;
        tree.root = root.copy();
        tree.nodeCount = nodeCount;
        tree.internalNodeCount = internalNodeCount;
        tree.leafNodeCount = leafNodeCount;
        tree.nTypes = nTypes;
        tree.typeLabel = typeLabel;
        return tree;
    }

    /**
     * Copy all values from an existing multi-type tree.
     *
     * @param other
     */
    @Override
    public void assignFrom(StateNode other) {
        MultiTypeTree mtTree = (MultiTypeTree) other;

        MultiTypeNode[] mtNodes = new MultiTypeNode[mtTree.getNodeCount()];
        for (int i=0; i<mtTree.getNodeCount(); i++)
            mtNodes[i] = new MultiTypeNode();

        m_sID = mtTree.m_sID;
        root = mtNodes[mtTree.root.getNr()];
        root.assignFrom(mtNodes, mtTree.root);
        root.m_Parent = null;

        nodeCount = mtTree.nodeCount;
        internalNodeCount = mtTree.internalNodeCount;
        leafNodeCount = mtTree.leafNodeCount;
        initArrays();
    }

    /**
     * Retrieve total number of allowed types on tree.
     *
     * @return total type/deme count.
     */
    public int getNTypes() {
        return nTypes;
    }
    
    /**
     * Check whether typing and timing of tree are sensible.
     * 
     * @return true if types and times are "valid"
     */
    public boolean isValid() {
        return timesAreValid(root) && typesAreValid(root);
    }
    
    private boolean timesAreValid(Node node) {
        for (Node child : node.getChildren()) {
            double lastHeight = node.getHeight();
            for (int idx=((MultiTypeNode)child).getChangeCount()-1; idx>=0; idx--) {
                double thisHeight = ((MultiTypeNode)child).getChangeTime(idx);
                if (thisHeight>lastHeight)
                    return false;
                lastHeight = thisHeight;
            }
            if (child.getHeight()>lastHeight)
                return false;
            
            if (!timesAreValid(child))
                return false;
        }
        
        return true;
    }

    private boolean typesAreValid(Node node) {
        for (Node child : node.getChildren()) {
            if (((MultiTypeNode)node).getNodeType() != ((MultiTypeNode)child).getFinalType())
                return false;
            
            if (!typesAreValid(child))
                return false;
        }
        
        return true;
    }
    
    /**
     * Generates a new tree in which the colours along the branches are
     * indicated by the traits of single-child nodes.
     *
     * This method is useful for interfacing trees coloured externally using the
     * a ColouredTree instance with methods designed to act on trees coloured
     * using single-child nodes and their metadata fields.
     *
     * Caveat: assumes more than one node exists on tree (i.e. leaf != root)
     *
     * @return Flattened tree.
     */
    public Tree getFlattenedTree() {

        // Create new tree to modify.  Note that copy() doesn't
        // initialise the node array lists, so initArrays() must
        // be called manually.
        Tree flatTree = copy();
        flatTree.initArrays();

        int nextNodeNr = getNodeCount();
        Node colourChangeNode;

        for (Node node : getNodesAsArray()) {

            MultiTypeNode mtNode = (MultiTypeNode)node;

            int nodeNum = node.getNr();

            if (node.isRoot()) {
                flatTree.getNode(nodeNum).setMetaData(typeLabel,
                        ((MultiTypeNode)(node.getLeft())).getFinalType());
                continue;
            }

            Node startNode = flatTree.getNode(nodeNum);

            startNode.setMetaData(typeLabel,
                    ((MultiTypeNode)node).getNodeType());
            startNode.m_sMetaData = String.format("%s=%d",
                    typeLabel, mtNode.getNodeType());

            Node endNode = startNode.getParent();
            
            endNode.setMetaData(typeLabel,
                    ((MultiTypeNode)node.getParent()).getNodeType());
            endNode.m_sMetaData = String.format("%s=%d",
                    typeLabel, ((MultiTypeNode)node.getParent()).getNodeType());

            Node branchNode = startNode;
            for (int i = 0; i<mtNode.getChangeCount(); i++) {

                // Create and label new node:
                colourChangeNode = new MultiTypeNode();
                colourChangeNode.setNr(nextNodeNr);
                colourChangeNode.setID(String.valueOf(nextNodeNr));
                nextNodeNr += 1;

                // Connect to child and parent:
                branchNode.setParent(colourChangeNode);
                colourChangeNode.addChild(branchNode);

                // Ensure height and colour trait are set:
                colourChangeNode.setHeight(mtNode.getChangeTime(i));
                colourChangeNode.setMetaData(typeLabel,
                        mtNode.getChangeType(i));
                colourChangeNode.m_sMetaData = String.format("%s=%d",
                        typeLabel, mtNode.getChangeType(i));

                // Update branchNode:
                branchNode = colourChangeNode;
            }

            // Ensure final branchNode is connected to the original parent:
            branchNode.setParent(endNode);
            if (endNode.getLeft()==startNode)
                endNode.setLeft(branchNode);
            else
                endNode.setRight(branchNode);
        }

        return flatTree;
    }

    /**
     * Initialise colours and tree topology from Tree object in which colour
     * changes are marked by single-child nodes and colours are stored in
     * metadata tags.
     *
     * @param flatTree
     */
    public void initFromFlatTree(Tree flatTree) throws Exception {


        // Obtain number of nodes and leaves in tree to construct:
        int nNodes = getTrueNodeCount(flatTree.root);
        int nLeaves = flatTree.root.getLeafNodeCount();

        // Initialise lists of available node numbers:
        List<Integer> leafNrs = new ArrayList<Integer>();
        List<Integer> internalNrs = new ArrayList<Integer>();

        for (int i = 0; i<nLeaves; i++)
            leafNrs.add(i);

        for (int i = 0; i<nNodes; i++)
            if (i<nLeaves)
                leafNrs.add(i);
            else
                internalNrs.add(i);

        // Build new coloured tree:

        List<Node> activeFlatTreeNodes = new ArrayList<Node>();
        List<Node> nextActiveFlatTreeNodes = new ArrayList<Node>();
        List<MultiTypeNode> activeTreeNodes = new ArrayList<MultiTypeNode>();
        List<MultiTypeNode> nextActiveTreeNodes = new ArrayList<MultiTypeNode>();

        // Populate active node lists with root:
        activeFlatTreeNodes.add(flatTree.getRoot());
        MultiTypeNode newRoot = new MultiTypeNode();
        activeTreeNodes.add(newRoot);

        while (!activeFlatTreeNodes.isEmpty()) {

            nextActiveFlatTreeNodes.clear();
            nextActiveTreeNodes.clear();

            for (int idx = 0; idx<activeFlatTreeNodes.size(); idx++) {
                Node flatTreeNode = activeFlatTreeNodes.get(idx);
                MultiTypeNode treeNode = activeTreeNodes.get(idx);

                Node thisFlatNode = flatTreeNode;
                List<Integer> colours = new ArrayList<Integer>();
                List<Double> times = new ArrayList<Double>();

                while (thisFlatNode.getChildCount()==1) {
                    int col = (int) Math.round(
                            (Double) thisFlatNode.getMetaData(typeLabel));
                    colours.add(col);
                    times.add(thisFlatNode.getHeight());

                    thisFlatNode = thisFlatNode.getLeft();
                }

                // Order changes from youngest to oldest:
                colours = Lists.reverse(colours);
                times = Lists.reverse(times);

                switch (thisFlatNode.getChildCount()) {
                    case 0:
                        // Leaf at base of branch
                        treeNode.setNr(leafNrs.get(0));
                        treeNode.setID(thisFlatNode.getID());
                        leafNrs.remove(0);
                        break;

                    case 2:
                        // Non-leaf at base of branch
                        treeNode.setNr(internalNrs.get(0));
                        internalNrs.remove(0);

                        nextActiveFlatTreeNodes.add(thisFlatNode.getLeft());
                        nextActiveFlatTreeNodes.add(thisFlatNode.getRight());

                        MultiTypeNode daughter = new MultiTypeNode();
                        MultiTypeNode son = new MultiTypeNode();
                        treeNode.setLeft(daughter);
                        treeNode.setRight(son);
                        nextActiveTreeNodes.add(daughter);
                        nextActiveTreeNodes.add(son);

                        break;
                }

                // Add type changes to multi-type tree branch:
                for (int i = 0; i<colours.size(); i++)
                    treeNode.addChange(colours.get(i), times.get(i));

                // Set node type at base of multi-type tree branch:
                int nodeType = (int) Math.round(
                        (Double) thisFlatNode.getMetaData(typeLabel));
                treeNode.setNodeType(nodeType);

                // Set node height:
                treeNode.setHeight(thisFlatNode.getHeight());
            }

            // Replace old active node lists with new:
            activeFlatTreeNodes.clear();
            activeFlatTreeNodes.addAll(nextActiveFlatTreeNodes);

            activeTreeNodes.clear();
            activeTreeNodes.addAll(nextActiveTreeNodes);

        }

        // Assign tree topology:
        assignFromWithoutID(new MultiTypeTree(newRoot));
        initArrays();
    }

    /**
     * Obtain total number of non-single-child nodes in subtree under node.
     *
     * @param node Root of subtree.
     * @return
     */
    private static int getTrueNodeCount(Node node) {
        if (node.isLeaf())
            return 1;

        if (node.getChildCount()>1)
            return 1+getTrueNodeCount(node.getLeft())
                    +getTrueNodeCount(node.getRight());

        return getTrueNodeCount(node.getLeft());
    }
    
    /**
     * Obtain total number of type changes along nodes on tree.
     * 
     * @return total change count
     */
    public int getTotalNumberOfChanges() {
        int count = 0;        

        for (Node node : m_nodes) {
            if (node.isRoot())
                continue;
            
            count += ((MultiTypeNode)node).getChangeCount();
        }
        
        return count;
    }

    /**
     * Return string representation of coloured tree.
     *
     * @return Coloured tree string in Newick format.
     */
    @Override
    public String toString() {
        return getFlattenedTree().getRoot().toNewick(null);
    }

    /////////////////////////////////////////////////
    //           StateNode implementation          //
    /////////////////////////////////////////////////
    @Override
    protected void store() {
        storedRoot = m_storedNodes[root.getNr()];
        int iRoot = root.getNr();

        storeNodes(0, iRoot);
        
        storedRoot.m_fHeight = m_nodes[iRoot].m_fHeight;
        storedRoot.m_Parent = null;

        if (root.getLeft()!=null)
            storedRoot.setLeft(m_storedNodes[root.getLeft().getNr()]);
        else
            storedRoot.setLeft(null);
        if (root.getRight()!=null)
            storedRoot.setRight(m_storedNodes[root.getRight().getNr()]);
        else
            storedRoot.setRight(null);
        
        MultiTypeNode mtStoredRoot = (MultiTypeNode)storedRoot;
        mtStoredRoot.changeTimes.clear();
        mtStoredRoot.changeTimes.addAll(((MultiTypeNode)m_nodes[iRoot]).changeTimes);

        mtStoredRoot.changeTypes.clear();
        mtStoredRoot.changeTypes.addAll(((MultiTypeNode)m_nodes[iRoot]).changeTypes);
        
        mtStoredRoot.nTypeChanges = ((MultiTypeNode)m_nodes[iRoot]).nTypeChanges;
        mtStoredRoot.nodeType = ((MultiTypeNode)m_nodes[iRoot]).nodeType;
        
        storeNodes(iRoot+1, nodeCount);
    }

    /**
     * helper to store *
     */
    private void storeNodes(int iStart, int iEnd) {
        for (int i = iStart; i<iEnd; i++) {
            MultiTypeNode sink = (MultiTypeNode)m_storedNodes[i];
            MultiTypeNode src = (MultiTypeNode)m_nodes[i];
            sink.m_fHeight = src.m_fHeight;
            sink.m_Parent = m_storedNodes[src.m_Parent.getNr()];
            if (src.getLeft()!=null) {
                sink.setLeft(m_storedNodes[src.getLeft().getNr()]);
                if (src.getRight()!=null)
                    sink.setRight(m_storedNodes[src.getRight().getNr()]);
                else
                    sink.setRight(null);
            }
            
            sink.changeTimes.clear();
            sink.changeTimes.addAll(src.changeTimes);
            
            sink.changeTypes.clear();
            sink.changeTypes.addAll(src.changeTypes);
            
            sink.nTypeChanges = src.nTypeChanges;
            sink.nodeType = src.nodeType;
        }
    }

    /////////////////////////////////////////////////
    // Methods implementing the Loggable interface //
    /////////////////////////////////////////////////
    @Override
    public void init(PrintStream printStream) throws Exception {

        printStream.println("#NEXUS\n");
        printStream.println("Begin taxa;");
        printStream.println("\tDimensions ntax="+getLeafNodeCount()+";");
        printStream.println("\t\tTaxlabels");
        for (int i = 0; i<getLeafNodeCount(); i++)
            printStream.println("\t\t\t"+getNodesAsArray()[i].getID());
        printStream.println("\t\t\t;");
        printStream.println("End;");

        printStream.println("Begin trees;");
        printStream.println("\tTranslate");
        for (int i = 0; i<getLeafNodeCount(); i++) {
            printStream.print("\t\t\t"+getNodesAsArray()[i].getNr()
                    +" "+getNodesAsArray()[i].getID());
            if (i<getLeafNodeCount()-1)
                printStream.print(",");
            printStream.print("\n");
        }
        printStream.print("\t\t\t;");
    }

    @Override
    public void log(int i, PrintStream printStream) {
        Tree flatTree = getFlattenedTree();
        printStream.print("tree STATE_"+i+" = ");
        String sNewick = flatTree.getRoot().toNewick(null);
        printStream.print(sNewick);
        printStream.print(";");


    }

    @Override
    public void close(PrintStream printStream) {
        printStream.println("End;");
    }
    
    
    /////////////////////////////////////////////////
    // Serialization and deserialization for state //
    /////////////////////////////////////////////////
    
    /**
     * reconstruct tree from XML fragment in the form of a DOM node *
     */
    @Override
    public void fromXML(org.w3c.dom.Node node) {
        try {
            String sNewick = node.getTextContent();

            TreeParser parser = new TreeParser();
            parser.initByName(
                    "IsLabelledNewick", true,
                    "adjustTipHeights", true,
                    "singlechild", true,
                    "newick", sNewick);
            Tree flatTree = parser;

            initFromFlatTree(flatTree);

            parser.m_nThreshold.setValue(1e-10, parser);

            parser.m_nOffset.setValue(0, parser);
            setRoot(parser.parseNewick(sNewick));

            initArrays();
        } catch (Exception ex) {
            Logger.getLogger(MultiTypeTree.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}

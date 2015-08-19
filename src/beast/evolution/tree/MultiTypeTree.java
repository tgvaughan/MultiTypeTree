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

import beast.app.beauti.BeautiDoc;
import beast.core.Citation;
import beast.core.Description;
import beast.core.Input;
import beast.core.StateNode;
import beast.core.StateNodeInitialiser;
import beast.util.TreeParser;
import com.google.common.collect.Lists;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("A multi-type phylogenetic tree.")
@Citation("Timothy G. Vaughan, Denise Kuhnert, Alex Popinga, David Welch and \n"
        + "Alexei J. Drummond, 'Efficient Bayesian inference under the \n"
        + "structured coalescent', Bioinformatics 30:2272, 2014.")
public class MultiTypeTree extends Tree {

    /*
     * Inputs:
     */
    public Input<String> typeLabelInput = new Input<>(
        "typeLabel",
        "Label for type traits (default 'type')", "type");

    public Input<TraitSet> typeTraitInput = new Input<>(
        "typeTrait", "Type trait set.  Used only by BEAUti.");

    public Input<List<String>> typeTraitValuesInput = new Input<>(
            "typeTraitValue",
            "An additional type value to be included even when absent " +
                    "from the sampled taxa.",
            new ArrayList<>());

    /*
     * Non-input fields:
     */
    protected String typeLabel;
    protected TraitSet typeTraitSet;
    
    protected List <String> typeList;

    public MultiTypeTree() { };
    
    public MultiTypeTree(Node rootNode) {
        
        if (!(rootNode instanceof MultiTypeNode))
            throw new IllegalArgumentException("Attempted to instantiate "
                    + "multi-type tree with regular root node.");
        
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
                left.labelNr = 0;
                left.height = 0;
                left.setID(sTaxa.get(0));
                for (int i = 1; i < sTaxa.size(); i++) {
                    Node right = new MultiTypeNode();
                    right.labelNr = i;
                    right.height = 0;
                    right.setID(sTaxa.get(i));
                    Node parent = new MultiTypeNode();
                    parent.labelNr = sTaxa.size() + i - 1;
                    parent.height = i;
                    left.parent = parent;
                    parent.setLeft(left);
                    right.parent = parent;
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
                root.labelNr = 0;
                root.labelNr = 0;
                root.m_tree = this;
                nodeCount = 1;
                internalNodeCount = 0;
                leafNodeCount = 1;
            }
        }

        if (nodeCount >= 0) {
            initArrays();
        }
        
        typeLabel = typeLabelInput.get();
        
        processTraits(m_traitList.get());

        // Ensure tree is compatible with traits.
        if (hasDateTrait())
            adjustTreeNodeHeights(root);
    }

    @Override
    protected void processTraits(List<TraitSet> traitList) {
        super.processTraits(traitList);
        
        // Record trait set associated with leaf types.
        for (TraitSet traitSet : traitList) {
            if (traitSet.getTraitName().equals(typeLabel)) {
                typeTraitSet = traitSet;
                break;
            }
        }

        // Use explicitly-identified type trait set if available.
        // Seems dumb, but needed for BEAUti as ListInputEditors
        // muck things up...
        if (typeTraitInput.get() != null)
            typeTraitSet = typeTraitInput.get();

        // Construct type list.
        if (typeTraitSet == null) {
            if (getTaxonset() != null) {
                TraitSet dummyTraitSet = new TraitSet();

                StringBuilder sb = new StringBuilder();
                for (int i=0; i<getTaxonset().getTaxonCount(); i++) {
                    if (i>0)
                        sb.append(",\n");
                    sb.append(getTaxonset().getTaxonId(i)).append("=NOT_SET");
                }
                try {
                    dummyTraitSet.initByName(
                        "traitname", "type",
                        "taxa", getTaxonset(),
                        "value", sb.toString());
                    dummyTraitSet.setID("typeTraitSet.t:"
                        + BeautiDoc.parsePartition(getID()));
                    setTypeTrait(dummyTraitSet);
                } catch (Exception ex) {
                    System.out.println("Error setting default type trait.");
                }
            }
        }

        if (typeTraitSet != null) {

            Set<String> typeSet = new HashSet<>();

            int nTaxa = typeTraitSet.taxaInput.get().asStringList().size();
            for (int i = 0; i < nTaxa; i++)
                typeSet.add(typeTraitSet.getStringValue(i));

            // Include any addittional trait values in type list
            for (String typeName : typeTraitValuesInput.get())
                typeSet.add(typeName);

            typeList = Lists.newArrayList(typeSet);
            Collections.sort(typeList);

            System.out.println("Type trait with the following types detected:");
            for (int i = 0; i < typeList.size(); i++)
                System.out.println(typeList.get(i) + " (" + i + ")");

        }
    }
    
    /**
     * @return TraitSet with same name as typeLabel.
     */
    public TraitSet getTypeTrait() {
        if (!traitsProcessed)
            processTraits(m_traitList.get());
        
        return typeTraitSet;
    }
    
    /**
     * @return true if TraitSet with same name as typeLabel exists.
     */
    public boolean hasTypeTrait() {
        return getTypeTrait() != null;
    }

    /**
     * Specifically set the type trait set for this tree. A null value simply
     * removes the existing trait set.
     *
     * @param traitSet
     */
    public void setTypeTrait(TraitSet traitSet) {
        if (hasTypeTrait()) {
            m_traitList.get().remove(typeTraitSet);
        }

        if (traitSet != null) {
            //m_traitList.setValue(traitSet, this);
            typeTraitInput.setValue(traitSet, this);
        }

        typeTraitSet = traitSet;
    }
    
    /**
     * Retrieve the list of unique types identified by the type trait.
     * @return List of unique type trait value strings.
     */
    public List<String> getTypeList() {
        if (!traitsProcessed)
            processTraits(m_traitList.get());
        
        return typeList;
    }

    /**
     * @param type
     * @return string name of given type
     */
    public String getTypeString(int type) {
        if (!traitsProcessed)
            processTraits(m_traitList.get());

        return typeList.get(type);
    }

    /**
     * @param typeString
     * @return integer type corresponding to given type string
     */
    public int getTypeFromString(String typeString) {
        if (!traitsProcessed)
            processTraits(m_traitList.get());

        return typeList.indexOf(typeString);
    }

    /**
     * @return type label to be used in logging.
     */
    public String getTypeLabel() {
        return typeLabel;
    }

    /**
     * Obtain the number of types defined for this MultiTypeTree.
     * Note that this is the number of _possible_ types, not the
     * number of types actually present on the tree.
     *
     * @return number of types defined for MultiTypeTree
     */
    public int getNTypes() {
        if (!traitsProcessed)
            processTraits(m_traitList.get());

        return typeList.size();
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
        tree.ID = ID;
        tree.index = index;
        tree.root = root.copy();
        tree.nodeCount = nodeCount;
        tree.internalNodeCount = internalNodeCount;
        tree.leafNodeCount = leafNodeCount;
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

        ID = mtTree.ID;
        root = mtNodes[mtTree.root.getNr()];
        root.assignFrom(mtNodes, mtTree.root);
        root.parent = null;

        nodeCount = mtTree.nodeCount;
        internalNodeCount = mtTree.internalNodeCount;
        leafNodeCount = mtTree.leafNodeCount;
        initArrays();
    }
    
    /**
     * Copy all values aside from IDs from an existing multi-type tree.
     * 
     * @param other
     */
    @Override
    public void assignFromFragile(StateNode other) {
        MultiTypeTree mtTree = (MultiTypeTree) other;
        if (m_nodes == null) {
            initArrays();
        }
        root = m_nodes[mtTree.root.getNr()];
        Node[] otherNodes = mtTree.m_nodes;
        int iRoot = root.getNr();
        assignFromFragileHelper(0, iRoot, otherNodes);
        root.height = otherNodes[iRoot].height;
        root.parent = null;
        
        MultiTypeNode mtRoot = (MultiTypeNode)root;
        mtRoot.nodeType = ((MultiTypeNode)(otherNodes[iRoot])).nodeType;
        mtRoot.changeTimes.clear();
        mtRoot.changeTypes.clear();
        mtRoot.nTypeChanges = 0;
        
        if (otherNodes[iRoot].getLeft() != null) {
            root.setLeft(m_nodes[otherNodes[iRoot].getLeft().getNr()]);
        } else {
            root.setLeft(null);
        }
        if (otherNodes[iRoot].getRight() != null) {
            root.setRight(m_nodes[otherNodes[iRoot].getRight().getNr()]);
        } else {
            root.setRight(null);
        }
        assignFromFragileHelper(iRoot + 1, nodeCount, otherNodes);
    }

    /**
     * helper to assignFromFragile *
     */
    private void assignFromFragileHelper(int iStart, int iEnd, Node[] otherNodes) {
        for (int i = iStart; i < iEnd; i++) {
            MultiTypeNode sink = (MultiTypeNode)m_nodes[i];
            MultiTypeNode src = (MultiTypeNode)otherNodes[i];
            sink.height = src.height;
            sink.parent = m_nodes[src.parent.getNr()];
            
            sink.nTypeChanges = src.nTypeChanges;
            sink.changeTimes.clear();
            sink.changeTimes.addAll(src.changeTimes);
            sink.changeTypes.clear();
            sink.changeTypes.addAll(src.changeTypes);
            sink.nodeType = src.nodeType;
            
            if (src.getLeft() != null) {
                sink.setLeft(m_nodes[src.getLeft().getNr()]);
                if (src.getRight() != null) {
                    sink.setRight(m_nodes[src.getRight().getNr()]);
                } else {
                    sink.setRight(null);
                }
            }
        }
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
     * @param useTypeStrings whether to use descriptive type strings
     * @return Flattened tree.
     */
    public Tree getFlattenedTree(boolean useTypeStrings) {

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
            if(useTypeStrings)
                startNode.metaDataString = String.format("%s=\"%s\"",
                    typeLabel, getTypeString(mtNode.getNodeType()));
            else
                startNode.metaDataString = String.format("%s=%d",
                    typeLabel, mtNode.getNodeType());

            Node endNode = startNode.getParent();
            
            endNode.setMetaData(typeLabel,
                    ((MultiTypeNode)node.getParent()).getNodeType());
            if(useTypeStrings)
                endNode.metaDataString = String.format("%s=\"%s\"",
                    typeLabel, getTypeString(((MultiTypeNode)node.getParent()).getNodeType()));
            else
                endNode.metaDataString = String.format("%s=%d",
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
                if (useTypeStrings)
                    colourChangeNode.metaDataString = String.format("%s=\"%s\"",
                        typeLabel, getTypeString(mtNode.getChangeType(i)));
                else
                    colourChangeNode.metaDataString = String.format("%s=%d",
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
     * meta-data tags. Node numbers of non-singleton nodes in flat tree
     * are preserved.
     *
     * @param flatTree
     * @param takeNrsFromFlatTree 
     * @throws java.lang.Exception 
     */
    public void initFromFlatTree(Tree flatTree, boolean takeNrsFromFlatTree) throws Exception {

        // Build new coloured tree:

        List<Node> activeFlatTreeNodes = new ArrayList<>();
        List<Node> nextActiveFlatTreeNodes = new ArrayList<>();
        List<MultiTypeNode> activeTreeNodes = new ArrayList<>();
        List<MultiTypeNode> nextActiveTreeNodes = new ArrayList<>();

        // Populate active node lists with root:
        activeFlatTreeNodes.add(flatTree.getRoot());
        MultiTypeNode newRoot = new MultiTypeNode();
        activeTreeNodes.add(newRoot);
        
        // Initialise counter used to number leaves when takeNrsFromFlatTree
        // is false:
        int nextNr = 0;

        while (!activeFlatTreeNodes.isEmpty()) {

            nextActiveFlatTreeNodes.clear();
            nextActiveTreeNodes.clear();

            for (int idx = 0; idx<activeFlatTreeNodes.size(); idx++) {
                Node flatTreeNode = activeFlatTreeNodes.get(idx);
                MultiTypeNode treeNode = activeTreeNodes.get(idx);

                List<Integer> colours = new ArrayList<>();
                List<Double> times = new ArrayList<>();

                while (flatTreeNode.getChildCount()==1) {
                    int col = (int) Math.round(
                            (Double) flatTreeNode.getMetaData(typeLabel));
                    colours.add(col);
                    times.add(flatTreeNode.getHeight());

                    flatTreeNode = flatTreeNode.getLeft();
                }

                // Order changes from youngest to oldest:
                colours = Lists.reverse(colours);
                times = Lists.reverse(times);

                switch (flatTreeNode.getChildCount()) {
                    case 0:
                        // Leaf at base of branch
                        if (takeNrsFromFlatTree) {
                            treeNode.setNr(flatTreeNode.getNr());
                            treeNode.setID(String.valueOf(flatTreeNode.getID()));
                        } else {
                            treeNode.setNr(nextNr);
                            treeNode.setID(String.valueOf(nextNr));
                            nextNr += 1;
                        }
                        break;

                    case 2:
                        // Non-leaf at base of branch
                        nextActiveFlatTreeNodes.add(flatTreeNode.getLeft());
                        nextActiveFlatTreeNodes.add(flatTreeNode.getRight());

                        MultiTypeNode daughter = new MultiTypeNode();
                        MultiTypeNode son = new MultiTypeNode();
                        treeNode.addChild(daughter);
                        treeNode.addChild(son);
                        nextActiveTreeNodes.add(daughter);
                        nextActiveTreeNodes.add(son);

                        break;
                }

                // Add type changes to multi-type tree branch:
                for (int i = 0; i<colours.size(); i++)
                    treeNode.addChange(colours.get(i), times.get(i));

                // Set node type at base of multi-type tree branch:
                Object typeObject = flatTreeNode.getMetaData(typeLabel);
                int nodeType;
                if (typeObject instanceof Integer)
                    nodeType = (int)flatTreeNode.getMetaData(typeLabel);
                else
                    nodeType = (int)Math.round((Double)flatTreeNode.getMetaData(typeLabel));
                treeNode.setNodeType(nodeType);

                // Set node height:
                treeNode.setHeight(flatTreeNode.getHeight());
            }

            // Replace old active node lists with new:
            activeFlatTreeNodes.clear();
            activeFlatTreeNodes.addAll(nextActiveFlatTreeNodes);

            activeTreeNodes.clear();
            activeTreeNodes.addAll(nextActiveTreeNodes);

        }
        
        
        // Number internal nodes:
        numberInternalNodes(newRoot, newRoot.getAllLeafNodes().size());
        
        // Assign tree topology:
        assignFromWithoutID(new MultiTypeTree(newRoot));
        initArrays();
        
    }
    
    /**
     * Helper method used by initFromFlattenedTree to assign sensible node numbers
     * to each internal node.  This is a post-order traversal, meaning the
     * root is given the largest number.
     * 
     * @param node
     * @param nextNr
     * @return 
     */
    private int numberInternalNodes(Node node, int nextNr) {
        if (node.isLeaf())
            return nextNr;
        
        for (Node child : node.getChildren())
            nextNr = numberInternalNodes(child, nextNr);
        
        node.setNr(nextNr);
        node.setID(String.valueOf(nextNr));
        
        return nextNr+1;
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
     * Return string representation of multi-type tree.  We use reflection
     * here to determine whether this is being called as part of writing
     * the state file.
     *
     * @return Multi-type tree string in Newick format.
     */
    @Override
    public String toString() {

        // Behaves differently if writing a state file
        StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        if (ste[2].getMethodName().equals("toXML")) {            
            // Use toShortNewick to generate Newick string without taxon labels
            String string = getFlattenedTree(false).getRoot().toShortNewick(true);
            
            // Sanitize ampersands if this is destined for a state file.
            return string.replaceAll("&", "&amp;");
        } else{
            return getFlattenedTree(true).getRoot().toSortedNewick(new int[1], true);
        }
    }

    /////////////////////////////////////////////////
    //           StateNode implementation          //
    /////////////////////////////////////////////////
    @Override
    protected void store() {
        storedRoot = m_storedNodes[root.getNr()];
        int iRoot = root.getNr();

        storeNodes(0, iRoot);
        
        storedRoot.height = m_nodes[iRoot].height;
        storedRoot.parent = null;

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
            sink.height = src.height;
            sink.parent = m_storedNodes[src.parent.getNr()];
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
            printStream.print("\t\t\t"+(getNodesAsArray()[i].getNr()+1)
                    +" "+getNodesAsArray()[i].getID());
            if (i<getLeafNodeCount()-1)
                printStream.print(",");
            printStream.print("\n");
        }
        printStream.print("\t\t\t;");
    }

    @Override
    public void log(int i, PrintStream printStream) {
        printStream.print("tree STATE_"+i+" = ");
        printStream.print(toString());
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
     * @param node
     */
    @Override
    public void fromXML(org.w3c.dom.Node node) {
        try {
            String sNewick = node.getTextContent().replace("&", "");

            TreeParser parser = new TreeParser();
            parser.initByName(
                    "IsLabelledNewick", false,
                    "offset", 0,
                    "adjustTipHeights", false,
                    "singlechild", true,
                    "newick", sNewick);
            //parser.m_nThreshold.setValue(1e-10, parser);
            //parser.m_nOffset.setValue(0, parser);
            
            initFromFlatTree(parser, true);

            initArrays();
        } catch (Exception ex) {
            Logger.getLogger(MultiTypeTree.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}

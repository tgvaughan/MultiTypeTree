package beast.evolution.tree;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.StateNode;
import beast.core.StateNodeInitialiser;
import beast.core.util.Log;
import beast.util.Randomizer;
import beast.util.TreeParser;
import org.jblas.MatrixFunctions;

import java.util.Arrays;
import java.util.List;

/**
 * @author dkuh004
 *         Date: Jun 18, 2012
 *         Time: 3:03:07 PM
 */

@Description("Class to initialize a MultiTypeTree from single child newick tree with type metadata")
public class MultiTypeTreeFromUntypedNewick extends MultiTypeTree implements StateNodeInitialiser {

    public Input<String> newickStringInput = new Input<>(
            "value",
            "Tree in Newick format.",
            Validate.REQUIRED);

    public Input<Boolean> adjustTipHeightsInput = new Input<>(
            "adjustTipHeights",
            "Adjust tip heights in tree? Default true.",
            false);

    public Input<SCMigrationModel> migrationModelInput = new Input<>(
            "migrationModel",
            "Migration model to use type simulation.",
            Validate.REQUIRED);

    SCMigrationModel migrationModel;
    Tree flatTree;
    MultiTypeNode[] typedNodes;

    public MultiTypeTreeFromUntypedNewick() {
    }

    @Override
    public void initAndValidate() {
        super.initAndValidate();

        migrationModel = migrationModelInput.get();

        // Read in flat tree
        flatTree = new TreeParser();
        flatTree.initByName(
                "IsLabelledNewick", true,
                "adjustTipHeights", adjustTipHeightsInput.get(),
                "singlechild", true,
                "newick", newickStringInput.get());

        // Create typed tree lacking type information
        typedNodes = new MultiTypeNode[flatTree.getNodeCount()];
        for (int i=0; i<typedNodes.length; i++) {
            typedNodes[i] = new MultiTypeNode();
            typedNodes[i].setNr(i);
            typedNodes[i].setHeight(flatTree.getNode(i).getHeight());
            typedNodes[i].setID(flatTree.getNode(i).getID());
        }
        for (int i=0; i<typedNodes.length; i++) {
            MultiTypeNode typedNode = typedNodes[i];
            Node node = flatTree.getNode(i);

            if (node.isRoot())
                typedNode.setParent(null);
            else
                typedNode.setParent(typedNodes[node.getParent().getNr()]);

            while (typedNode.children.size() < node.getChildCount())
                typedNode.children.add(null);

            for (int c=0; c<node.getChildCount(); c++) {
                typedNode.setChild(c, typedNodes[node.getChild(c).getNr()]);
            }
        }

        // Assign node types
        if (getTypeTrait() != null) {
            for (int i=0; i<flatTree.getLeafNodeCount(); i++) {
                MultiTypeNode typedNode = typedNodes[i];
                typedNode.setNodeType(migrationModel.getTypeSet().getTypeIndex(
                        getTypeTrait().getStringValue(typedNode.getID())));
            }
        } else {
            throw new IllegalArgumentException(
                    "Trait set (with name '" + typeLabelInput.get() + "') "
                            + "must be provided.");
        }

        // Clear any existing type info
        while (true) {
            for (int i = 0; i < flatTree.getNodeCount(); i++) {
                typedNodes[i].clearChanges();

                if (i >= flatTree.getLeafNodeCount())
                    typedNodes[i].setNodeType(-1);
            }

            int leafNrs[] = Randomizer.shuffled(flatTree.getLeafNodeCount());


            try {
                boolean isFirst = true;
                for (int leafNr : leafNrs) {
                    if (isFirst) {
                        colourFirstLineage(typedNodes[leafNr]);
                        isFirst = false;
                    } else {
                        colourLineage(typedNodes[leafNr]);
                    }
                }
            } catch (NoValidPathException ex) {
                Log.info.println("Colour simulation failed. Retrying.");
                continue;
            }

            break;
        }

        // Construct MTT

        root = typedNodes[typedNodes.length-1];
        root.parent = null;
        nodeCount = root.getNodeCount();
        internalNodeCount = root.getInternalNodeCount();
        leafNodeCount = root.getLeafNodeCount();

        initArrays();
    }

    void colourFirstLineage(MultiTypeNode leaf) {

        MultiTypeNode thisNode = leaf;
        double time = thisNode.getHeight();
        int type = thisNode.getNodeType();
        MultiTypeNode nextNode = (MultiTypeNode)thisNode.getParent();

        while (true) {

            double aTot = 0;
            for (int c=0; c<migrationModel.getNTypes(); c++) {
                if (c == type)
                    continue;

                aTot += migrationModel.getBackwardRate(type, c);
            }

            double newTime = time + Randomizer.nextExponential(aTot);

            while (nextNode != null && nextNode.getHeight() < newTime) {
                nextNode.setNodeType(type);
                thisNode = nextNode;
                nextNode = (MultiTypeNode)nextNode.getParent();
            }

            if (nextNode == null)
                break;

            time = newTime;

            double u = Randomizer.nextDouble()*aTot;

            int newType;
            for (newType=0; newType<migrationModel.getNTypes(); newType++) {
                if (newType==type)
                    continue;

                u -= migrationModel.getBackwardRate(type, newType);
                if (u<0.0)
                    break;
            }

            thisNode.addChange(newType, time);
            type = newType;
        }
    }


    void colourLineage(MultiTypeNode leaf) throws NoValidPathException {

        // Find first coloured ancestral node
        MultiTypeNode firstColouredAncestor = (MultiTypeNode)leaf.getParent();
        while (firstColouredAncestor.getNodeType()<0)
            firstColouredAncestor = (MultiTypeNode) firstColouredAncestor.getParent();

        addTypeChanges(leaf.getNodeType(), firstColouredAncestor.getNodeType(),
                leaf.getHeight(), firstColouredAncestor.getHeight(), leaf);
    }

    /**
     * Exception used to signal non-existence of allowed type sequence
     * between node types.
     */
    protected class NoValidPathException extends Exception {
        @Override
        public String getMessage() {
            return "No valid valid type sequence exists between chosen nodes.";
        }
    }

    /**
     * Sample the number of virtual events to occur along branch.
     *
     * General strategy here is to:
     * 1. Draw u from Unif(0,1),
     * 2. Starting from zero, evaluate P(n leq 0|a,b) up until n=thresh
     * or P(n leq 0|a,b)>u.
     * 3. If P(n leq 0|a,b) has exceeded u, use that n. If not, use rejection
     * sampling to draw conditional on n being >= thresh.
     *
     * @param typeStart Type at start (bottom) of branch
     * @param typeEnd Type at end (top) of branch
     * @param muL Expected unconditioned number of virtual events
     * @param Pba Probability of final type given start type
     * @param migrationModel Migration model to use.
     * @return number of virtual events.
     */
    private int drawEventCount(int typeStart, int typeEnd, double muL, double Pba,
                               MigrationModel migrationModel, boolean sym) {

        int nVirt = 0;

        double u = Randomizer.nextDouble();
        double P_low_given_ab = 0.0;
        double acc = - muL - Math.log(Pba);
        double log_muL = Math.log(muL);

        do {
            //double offset = acc + nVirt*log_muL - Gamma.logGamma(nVirt+1);
            P_low_given_ab += Math.exp(Math.log(migrationModel.getRpowN(nVirt, sym).get(typeStart, typeEnd)) + acc);

            if (P_low_given_ab>u)
                return nVirt;

            nVirt += 1;
            acc += log_muL - Math.log(nVirt);

        } while (migrationModel.RpowSteadyN(sym)<0 || nVirt<migrationModel.RpowSteadyN(sym));

        int thresh = nVirt;

        // P_n_given_ab constant for n>= thresh: only need
        // to sample P(n|n>=thresh)
        do {
            nVirt = (int) Randomizer.nextPoisson(muL);
        } while (nVirt < thresh);

        return nVirt;
    }

    /**
     * Retype branch between srcNode and its parent.  Uses the combined
     * uniformization/forward-backward approach of Fearnhead and Sherlock (2006)
     * to condition on both the beginning and end states.
     */
    private void addTypeChanges(int startType, int endType,
                                    double startTime, double endTime,
                                    MultiTypeNode startNode) throws NoValidPathException {
        double L = endTime - startTime;

        // Pre-calculate some stuff:
        double muL = migrationModel.getMu(false)*L;

        double Pba = MatrixFunctions.expm(
                migrationModel.getQ(false)
                        .mul(L)).get(startType,endType);

        // Abort if transition is impossible.
        if (Pba == 0.0)
            throw new NoValidPathException();

        // Catch for numerical errors
        if (Pba>1.0 || Pba<0.0) {
            System.err.println("Warning: matrix exponentiation resulted in rubbish.  Aborting simulation.");
            return;
        }

        // Select number of virtual events:
        int nVirt = drawEventCount(startType, endType, muL, Pba,
                migrationModel, false);

        if (nVirt<0)
            return;

        // Select times of virtual events:
        double[] times = new double[nVirt];
        for (int i = 0; i<nVirt; i++)
            times[i] = Randomizer.nextDouble()*L+startTime;
        Arrays.sort(times);

        // Sample type changes along branch using FB algorithm:
        int[] types = new int[nVirt];
        int prevType = startType;

        for (int i = 1; i<=nVirt; i++) {

            double u2 = Randomizer.nextDouble()
                    *migrationModel.getRpowN(nVirt-i+1, false).get(prevType, endType);
            int c;
            boolean fellThrough = true;
            for (c = 0; c<migrationModel.getNTypes(); c++) {
                u2 -= migrationModel.getR(false).get(prevType,c)
                        *migrationModel.getRpowN(nVirt-i, false).get(c,endType);
                if (u2<0.0) {
                    fellThrough = false;
                    break;
                }
            }

            // Check for FB algorithm error:
            if (fellThrough) {
                System.err.println("Warning: FB algorithm failure.  Aborting simulation.");
                return;
            }

            types[i-1] = c;
            prevType = c;
        }

        // Add non-virtual type changes to branch, calculating probability
        // of path conditional on start type:
        prevType = startType;
        MultiTypeNode prevNode = startNode;
        MultiTypeNode nextNode = (MultiTypeNode) startNode.getParent();
        for (int i = 0; i<nVirt; i++) {

            if (types[i] != prevType) {

                // Colour any internal nodes we pass:
                while (times[i] > nextNode.getHeight()) {
                    nextNode.setNodeType(prevType);
                    prevNode = nextNode;
                    nextNode = (MultiTypeNode) nextNode.getParent();
                }

                // Add change to branch:
                prevNode.addChange(types[i], times[i]);

                prevType = types[i];
            }
        }

        // Colour any internal nodes between last migration time and end time
        while (nextNode.getHeight() < endTime) {
            nextNode.setNodeType(prevType);

            nextNode = (MultiTypeNode) nextNode.getParent();
        }
    }

    @Override
    public void initStateNodes() { }

    @Override
    public void getInitialisedStateNodes(List<StateNode> stateNodeList) {
        stateNodeList.add(this);
    }
}

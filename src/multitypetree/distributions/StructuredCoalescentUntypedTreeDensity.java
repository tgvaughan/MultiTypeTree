package multitypetree.distributions;

import beast.core.Input;
import beast.core.parameter.IntegerParameter;
import beast.evolution.tree.*;
import beast.util.Randomizer;
import org.jblas.MatrixFunctions;

import java.util.*;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class StructuredCoalescentUntypedTreeDensity extends TreeDistribution {

    public Input<SCMigrationModel> migrationModelInput = new Input<>(
            "migrationModel", "Model of migration between demes.",
            Input.Validate.REQUIRED);

    public Input<Integer> nParticlesInput = new Input<>(
            "nParticles",
            "Number of simulated trees used in density estimate.",
            Input.Validate.REQUIRED);

    public Input<String> typeLabelInput = new Input<>(
            "typeLabel",
            "Label for type traits (default 'type')", "type");

    int nParticles;
    double[] logParticleWeights;
    Tree tree;
    SCMigrationModel migrationModel;

    private enum SCEventKind {
        COALESCE, MIGRATE, SAMPLE
    };

    private class SCEvent implements Comparable<SCEvent> {
        double time;
        int type, destType;
        SCEventKind kind;

        @Override
        public int compareTo(SCEvent o) {
            if (this.time < o.time)
                return -1;

            if (this.time > o.time)
                return 1;

            return 0;
        }

        @Override
        public String toString() {
            return kind
                    + " " + type + (kind == SCEventKind.MIGRATE ? "->" + destType : "")
                    + " (" + time + ")";
        }
    }

    List<SCEvent> eventList;
    int[] lineageCount;
    int[] nodeTypes;

    public StructuredCoalescentUntypedTreeDensity() {
        eventList = new ArrayList<>();
    }


    @Override
    public void initAndValidate() {
        super.initAndValidate();

        nParticles = nParticlesInput.get();
        tree = (Tree) treeInput.get();
        migrationModel = migrationModelInput.get();
        lineageCount = new int[migrationModel.getNTypes()];
        logParticleWeights = new double[nParticles];
        nodeTypes = new int[tree.getNodeCount()];

        // Fill leaf colour array:
        TraitSet typeTraitSet = null;
        for (TraitSet traitSet : tree.m_traitList.get()) {
            if (traitSet.getTraitName().equals(typeLabelInput.get())) {
                typeTraitSet = traitSet;
                break;
            }
        }
        if (typeTraitSet != null) {

            Set<String> typeSet = new TreeSet<>();
            for (String taxon : tree.getTaxaNames()) {
                typeSet.add(typeTraitSet.getStringValue(taxon));
            }
            List<String> typeList = new ArrayList<>(typeSet);

            for (Node leaf : tree.getExternalNodes()) {
                nodeTypes[leaf.getNr()] = typeList.indexOf(typeTraitSet.getStringValue(leaf.getID()));
            }
        } else {
            throw new IllegalArgumentException(
                    "Trait set (with name '" + typeLabelInput.get() + "') "
                            + "must be provided.");
        }

    }


    @Override
    public double calculateLogP() {
        logP = 0.0;

        double maxLogWeight = Double.NEGATIVE_INFINITY;

        for (int p=0; p<nParticles; p++) {
            eventList.clear();
            logParticleWeights[p] = 0;

            // Clear internal node types:
            for (int i = tree.getLeafNodeCount(); i < tree.getNodeCount(); i++)
                nodeTypes[i] = -1;

            // Choose random order in which to colour lineages:
            int leafNrs[] = Randomizer.shuffled(tree.getLeafNodeCount());

            boolean isFirst = true;
            for (int leafNr : leafNrs) {

                if (isFirst) {
                    logParticleWeights[p] -= colourFirstLineage(leafNr);
//                    printAncestralColours(leafNr);
                    isFirst = false;
                } else {
                    logParticleWeights[p] -= colourLineage(leafNr);
//                    printAncestralColours(leafNr);
                }
            }

            Collections.sort(eventList);

            for (int c=0; c<migrationModel.getNTypes(); c++)
                lineageCount[c] = eventList.get(0).type == c ? 1 : 0;

            for (int eventIdx = 1; eventIdx<eventList.size(); eventIdx++) {

                SCEvent event = eventList.get(eventIdx);
                double delta_t = event.time - eventList.get(eventIdx - 1).time;

                // Interval contribution:
                if (delta_t > 0) {
                    double lambda = 0.0;
                    for (int c = 0; c < lineageCount.length; c++) {
                        int k = lineageCount[c];
                        double Nc = migrationModel.getPopSize(c);
                        lambda += k * (k - 1) / (2.0 * Nc);

                        for (int cp = 0; cp < lineageCount.length; cp++) {
                            if (cp == c)
                                continue;

                            double m = migrationModel.getBackwardRate(c, cp);
                            lambda += k * m;
                        }
                    }
                    logParticleWeights[p] += -delta_t * lambda;
                }

                // Event contribution:
                switch (event.kind) {
                    case COALESCE:
                        double N = migrationModel.getPopSize(event.type);
                        logParticleWeights[p] += Math.log(1.0 / N);
                        lineageCount[event.type] -= 1;
                        break;

                    case MIGRATE:
                        double m = migrationModel
                                .getBackwardRate(event.type, event.destType);
                        logParticleWeights[p] += Math.log(m);
                        lineageCount[event.type] -= 1;
                        lineageCount[event.destType] += 1;
                        break;

                    case SAMPLE:
                        lineageCount[event.type] += 1;
                        break;
                }
            }

            maxLogWeight = Math.max(logParticleWeights[p], maxLogWeight);
        }

        double sumScaledWeights = 0;
        for (int p=0; p<nParticles; p++) {
            sumScaledWeights += Math.exp(logParticleWeights[p] - maxLogWeight);
        }

        logP = Math.log(sumScaledWeights/nParticles) + maxLogWeight;

        return logP;
    }

    /**
     * Colour first lineage of tree.  This is handled specially because
     * this CTMC is not conditioned on an earlier node type.
     *
     * @param leafNr number of starting leaf
     * @return log probability of simulated path
     */
    double colourFirstLineage(int leafNr) {

        Node leaf = tree.getNode(leafNr);
        double time = leaf.getHeight();
        int type = nodeTypes[leafNr];
        Node nextNode = leaf.getParent();

        SCEvent event = new SCEvent();
        event.kind = SCEventKind.SAMPLE;
        event.type = type;
        event.time = leaf.getHeight();
        eventList.add(event);


        double thisLogP = 0.0;

        while (true) {

            double aTot = 0;
            for (int c=0; c<migrationModel.getNTypes(); c++) {
                if (c == type)
                    continue;

                aTot += migrationModel.getBackwardRate(type, c);
            }

            double newTime = time + Randomizer.nextExponential(aTot);

            while (nextNode != null && nextNode.getHeight() < newTime) {
                nodeTypes[nextNode.getNr()] = type;

                event = new SCEvent();
                event.kind = SCEventKind.COALESCE;
                event.type = type;
                event.time = nextNode.getHeight();
                eventList.add(event);

                thisLogP += -aTot*(nextNode.getHeight() - time);
                time = nextNode.getHeight();

                nextNode = nextNode.getParent();
            }

            if (nextNode == null)
                break;

            thisLogP += -aTot*(newTime - time);
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

            event = new SCEvent();
            event.kind = SCEventKind.MIGRATE;
            event.type = type;
            event.destType = newType;
            event.time = time;
            eventList.add(event);

            thisLogP += Math.log(migrationModel.getBackwardRate(type, newType));
            type = newType;
        }

        return thisLogP;
    }

    double colourLineage(int leafNr) {
        double thisLogP = 0.0;

        Node leaf = tree.getNode(leafNr);

        SCEvent event = new SCEvent();
        event.kind = SCEventKind.SAMPLE;
        event.type = nodeTypes[leafNr];
        event.time = leaf.getHeight();
        eventList.add(event);

        // Find first coloured ancestral node
        Node firstColouredAncestor = leaf.getParent();
        while (nodeTypes[firstColouredAncestor.getNr()]<0)
            firstColouredAncestor = firstColouredAncestor.getParent();

        try {
            thisLogP += addTypeChanges(nodeTypes[leafNr], nodeTypes[firstColouredAncestor.getNr()],
                    leaf.getHeight(), firstColouredAncestor.getHeight(), leaf);
        } catch (NoValidPathException e) {
            return Double.NEGATIVE_INFINITY;
        }

        return thisLogP;
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
     *
     * @return Probability of new state.
     * @throws multitypetree.operators.UniformizationRetypeOperator.NoValidPathException
     */
    protected double addTypeChanges(int startType, int endType, double startTime, double endTime, Node startNode) throws NoValidPathException {
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
            System.err.println("Warning: matrix exponentiation resulted in rubbish.  Aborting move.");
            return Double.NEGATIVE_INFINITY;
        }

        // Select number of virtual events:
        int nVirt = drawEventCount(startType, endType, muL, Pba,
                migrationModel, false);

        if (nVirt<0)
            return Double.NEGATIVE_INFINITY;

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
                System.err.println("Warning: FB algorithm failure.  Aborting move.");
                return Double.NEGATIVE_INFINITY;
            }

            types[i-1] = c;
            prevType = c;
        }

        double logProb = 0.0;

        // Add non-virtual type changes to branch, calculating probability
        // of path conditional on start type:
        prevType = startType;
        double prevTime = startTime;
        Node prevNode = startNode;
        for (int i = 0; i<nVirt; i++) {

            if (types[i] != prevType) {

                // Add change to branch:
                SCEvent event = new SCEvent();
                event.kind = SCEventKind.MIGRATE;
                event.type = prevType;
                event.destType = types[i];
                event.time = times[i];
                eventList.add(event);

                // Colour any internal nodes we pass:
                while (prevNode.getHeight() < times[i]) {
                    if (!prevNode.isLeaf()) {
                        nodeTypes[prevNode.getNr()] = prevType;

                        event = new SCEvent();
                        event.kind = SCEventKind.COALESCE;
                        event.type = prevType;
                        event.time = prevNode.getHeight();
                        eventList.add(event);

                    }
                    prevNode = prevNode.getParent();
                }

                // Add probability contribution:
                logProb += migrationModel.getQ(false).get(prevType, prevType)*(times[i]-prevTime)
                        +Math.log(migrationModel.getQ(false).get(prevType, types[i]));

                prevType = types[i];
                prevTime = times[i];
            }
        }
        logProb += migrationModel.getQ(false).get(prevType, prevType)*(endTime-prevTime);

        // Colour any internal nodes between last migration time and end time
        while (prevNode.getHeight() < endTime) {
            if (!prevNode.isLeaf()) {
                nodeTypes[prevNode.getNr()] = prevType;

                SCEvent event = new SCEvent();
                event.kind = SCEventKind.COALESCE;
                event.type = prevType;
                event.time = prevNode.getHeight();
                eventList.add(event);

            }
            prevNode = prevNode.getParent();
        }

        // Adjust probability to account for end condition:
        logProb -= Math.log(Pba);

        // Return probability of path given boundary conditions:
        return logProb;
    }

    public void printAncestralColours(int leafNr) {
        Node node = tree.getNode(leafNr);

        while (node != null) {
            if (!node.isLeaf())
                System.out.print(", ");
            System.out.print(node.getNr() + " " + nodeTypes[node.getNr()]);
            node = node.getParent();
        }
        System.out.println();
    }

    @Override
    protected boolean requiresRecalculation() {
        return true;
    }

    @Override
    public boolean isStochastic() {
        return true;
    }
}

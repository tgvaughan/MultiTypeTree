package multitypetree.operators;

import beast.core.Input;
import beast.core.Operator;
import beast.core.parameter.RealParameter;
import beast.evolution.operators.ScaleOperator;
import beast.evolution.tree.SCMigrationModel;
import beast.util.CollectionUtils;
import beast.util.Randomizer;

import java.util.Arrays;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class PopSizeScaler extends Operator {

    public final Input<SCMigrationModel> migrationModelInput = new Input<>(
            "migrationModel",
            "Migration model in which population sizes are defined.",
            Input.Validate.REQUIRED);

    public final Input<Double> scaleFactorInput = new Input<>(
            "scaleFactor",
            "scaling factor: larger means more bold proposals", 1.0);

    public final Input<Boolean> ensureOrderingInput = new Input<>(
            "ensurePopSizeOrdering",
            "Ensure labels are such that N0<N1<...", false);

    SCMigrationModel migModel;
    boolean ensureOrdering;

    @Override
    public void initAndValidate() {
        migModel = migrationModelInput.get();
        ensureOrdering = ensureOrderingInput.get();
    }

    public double proposal() {
        double fmin = Math.min(scaleFactorInput.get(), 1.0 / scaleFactorInput.get());
        double f = fmin + (1.0 / fmin - fmin) * Randomizer.nextDouble();

        // Select population to scale
        int type = Randomizer.nextInt(migModel.getNTypes());

        RealParameter popParam = migModel.popSizesInput.get();

        popParam.setValue(popParam.getValue(type) * f);
        if (popParam.getValue(type) > popParam.getUpper() || popParam.getValue(type) < popParam.getLower())
            return Double.NEGATIVE_INFINITY;

        // Switch labels to ensure population size ordering
        IndexValuePair[] pairs = new IndexValuePair[migModel.getNTypes()];
        for (int i = 0; i < pairs.length; i++)
            pairs[i] = new IndexValuePair(i, popParam.getValue(i));
        Arrays.sort(pairs);

        boolean switchNeeded = false;
        for (int i = 1; i < pairs.length; i++) {
            if (pairs[i].index < pairs[i - 1].index) {
                switchNeeded = true;
                break;
            }
        }

        if (switchNeeded) {
            
        }

        return -Math.log(f);
    }

    class IndexValuePair implements Comparable<IndexValuePair> {

        int index;
        double value;

        public IndexValuePair(int index, double value) {
            this.index = index;
            this.value = value;
        }

        @Override
        public int compareTo(IndexValuePair o) {
            if (value < o.value)
                return -1;

            if (value > o.value)
                return 1;

            return 0;
        }
    }

}

package beast.evolution.tree;

import org.jblas.DoubleMatrix;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public interface MigrationModelInterface {
    int getNTypes();

    double getRate(int i, int j);

    double getForwardsRate(int i, int j);

    void setRate(int i, int j, double rate);

    double getMu(boolean symmetric);

    DoubleMatrix getR(boolean symmetric);

    DoubleMatrix getQ(boolean symmetric);

    DoubleMatrix getRpowN(int n, boolean symmetric);

    DoubleMatrix getRpowMax(boolean symmetric);

    int RpowSteadyN(boolean symmetric);
}

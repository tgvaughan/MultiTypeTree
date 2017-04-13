package beast.evolution.tree;

import beast.core.BEASTInterface;
import org.jblas.DoubleMatrix;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public interface MigrationModel extends BEASTInterface {
    int getNTypes();

    TypeSet getTypeSet();

    double getBackwardRate(int i, int j);

    double getForwardRate(int i, int j);

    double getMu(boolean symmetric);

    DoubleMatrix getR(boolean symmetric);

    DoubleMatrix getQ(boolean symmetric);

    DoubleMatrix getRpowN(int n, boolean symmetric);

    int RpowSteadyN(boolean symmetric);

}

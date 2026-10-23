package multitypetree.evolution.tree;

import beast.base.core.BEASTInterface;
import org.apache.commons.math3.linear.RealMatrix;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public interface MigrationModel extends BEASTInterface {
    int getNTypes();

    TypeSet getTypeSet();

    double getBackwardRate(int i, int j);

    double getForwardRate(int i, int j);

    double getMu(boolean symmetric);

    RealMatrix getR(boolean symmetric);

    RealMatrix getQ(boolean symmetric);

    RealMatrix getRpowN(int n, boolean symmetric);

    int RpowSteadyN(boolean symmetric);

}

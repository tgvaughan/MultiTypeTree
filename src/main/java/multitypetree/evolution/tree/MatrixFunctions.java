package multitypetree.evolution.tree;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

public class MatrixFunctions {

    static int maxOrder = 100;

    public static RealMatrix expm(RealMatrix A) {

        RealMatrix X = MatrixUtils.createRealIdentityMatrix(A.getColumnDimension());
        RealMatrix Z = MatrixUtils.createRealIdentityMatrix(A.getColumnDimension());

        for (int n=1; n<=maxOrder; n++) {
            X = X.multiply(A).scalarMultiply(1.0/n);
            Z = Z.add(X);
        }

        return Z;
    }
}

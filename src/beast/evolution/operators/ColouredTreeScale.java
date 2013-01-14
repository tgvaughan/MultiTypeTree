/*
 * Copyright (C) 2012 Tim Vaughan <tgvaughan@gmail.com>
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
package beast.evolution.operators;

import beast.core.Description;
import beast.core.Input;
import beast.core.parameter.BooleanParameter;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.Node;
import beast.util.Randomizer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Scale operator for coloured trees.  Also allows additional "
        + "scalar parameters to be rescaled (either forward or inversely) "
        + "at the same time.")
public class ColouredTreeScale extends ColouredTreeOperator {
    
    public Input<List<RealParameter>> parametersInput =
            new Input<List<RealParameter>>("parameter",
            "Scale this scalar parameter by the same amount as tree.",
            new ArrayList<RealParameter>());
    
    public Input<List<BooleanParameter>> indicatorsInput =
            new Input<List<BooleanParameter>>("indicator",
            "If provided, used to specify a subset of parameter elements to scale.",
            new ArrayList<BooleanParameter>());
    
    public Input<List<RealParameter>> parametersInverseInput =
            new Input<List<RealParameter>>("parameterInverse",
            "Scale this scalar parameter inversely.",
            new ArrayList<RealParameter>());
    
    public Input<List<BooleanParameter>> indicatorsInverseInput =
            new Input<List<BooleanParameter>>("indicatorInverse",
            "If provided, used to specify a subset of parameter elements to scale "
            + "inversely.",
            new ArrayList<BooleanParameter>());

    public Input<Double> scaleFactorInput = new Input<Double>("scaleFactor",
            "Scaling is restricted to the range [1/scaleFactor, scaleFactor]");
    
    public Input<Boolean> useOldTreeScalerInput = new Input<Boolean>(
            "useOldTreeScaler",
            "Use original coloured tree scaling algorithm. (Default false.)",
            false);
    
    boolean indicatorsUsed, indicatorsInverseUsed;
    
    @Override
    public void initAndValidate() {

        if (indicatorsInput.get().size()>0) {
            if (indicatorsInput.get().size() != parametersInput.get().size())
                throw new IllegalArgumentException("If an indicator element "
                        + "exists, the number of such elements must equal "
                        + "the number of parameter elements.");
            
            for (int pidx=0; pidx<parametersInput.get().size(); pidx++) {
                if (parametersInput.get().get(pidx).getDimension() != 
                        indicatorsInput.get().get(pidx).getDimension()) {
                    throw new IllegalArgumentException("The number of boolean "
                            + "values in indicator element "
                            + String.valueOf(pidx+1)
                            + " doesn't match the dimension of the "
                            + "corresponding parameter element.");
                }
            }
            indicatorsUsed = true;
        } else
            indicatorsUsed = false;
        
        if (indicatorsInverseInput.get().size()>0) {
            if (indicatorsInverseInput.get().size() != parametersInverseInput.get().size())
                throw new IllegalArgumentException("If an indicatorInverse element "
                        + "exists, the number of such elements must equal "
                        + "the number of parameterInverse elements.");
            
            for (int pidx=0; pidx<parametersInverseInput.get().size(); pidx++) {
                if (parametersInverseInput.get().get(pidx).getDimension() != 
                        indicatorsInverseInput.get().get(pidx).getDimension()) {
                    throw new IllegalArgumentException("The number of boolean "
                            + "values in indicatorInverse element "
                            + String.valueOf(pidx+1)
                            + " doesn't match the dimension of the "
                            + "corresponding parameterInverse element.");
                }
            }
            indicatorsInverseUsed = true;
        } else
            indicatorsInverseUsed = false;
    }

    @Override
    public double proposal() {

        cTree = colouredTreeInput.get();
        tree = cTree.getUncolouredTree();

        // Choose scale factor:
        double u = Randomizer.nextDouble();
        double f = u*scaleFactorInput.get()+(1.0-u)/scaleFactorInput.get();

        // Keep track of Hastings ratio:
        double logf = Math.log(f);
        double logHR = -2*logf;
        
        // Scale colour change times on external branches:        
        if (!useOldTreeScalerInput.get()) {
            for (Node leaf : tree.getExternalNodes()) {
                double lold = leaf.getParent().getHeight()-leaf.getHeight();
                double lnew = f*leaf.getParent().getHeight()-leaf.getHeight();            
                
                for (int c=0; c<cTree.getChangeCount(leaf); c++) {
                    double oldTime = cTree.getChangeTime(leaf, c);
                    double newTime = leaf.getHeight()
                            + (oldTime-leaf.getHeight())*lnew/lold;
                    setChangeTime(leaf, c, newTime);
                }
                
                logHR += cTree.getChangeCount(leaf)*Math.log(lnew/lold);
            }
        } else {
            for (Node leaf : tree.getExternalNodes()) {
                for (int c = 0; c<cTree.getChangeCount(leaf); c++) {
                    double oldTime = cTree.getChangeTime(leaf, c);
                    setChangeTime(leaf, c, f*oldTime);
                    logHR += logf;
                }
            }
        }

        // Scale internal node heights and colour change times:
        for (Node node : tree.getInternalNodes()) {
            
            node.setHeight(node.getHeight()*f);
            logHR += logf;
            
            for (int c = 0; c<cTree.getChangeCount(node); c++) {
                double oldTime = cTree.getChangeTime(node, c);
                setChangeTime(node, c, f*oldTime);
                logHR += logf;
            }
        }
        
        // Ensure reject invalid tree scalings:
        if (f<1.0) {
            for (Node leaf : tree.getExternalNodes()) {
                if (leaf.getParent().getHeight()<leaf.getHeight())
                    return Double.NEGATIVE_INFINITY;
                
                if (cTree.getChangeCount(leaf)>0 && cTree.getChangeTime(leaf, 0)<leaf.getHeight())
                    if (useOldTreeScalerInput.get())
                        throw new IllegalStateException("Scaled colour change time "
                                + "has dipped below age of leaf - this should never "
                                + "happen!");
                    else
                        return Double.NEGATIVE_INFINITY;
            }
        }
        
        // Scale parameters:
        for (int pidx=0; pidx<parametersInput.get().size(); pidx++) {
            RealParameter param = parametersInput.get().get(pidx);
            for (int i=0; i<param.getDimension(); i++) {
                if (!indicatorsUsed ||
                        indicatorsInput.get().get(pidx).getValue(i)) {
                    double oldValue = param.getValue(i);
                    param.setValue(i, oldValue*f);
                    logHR += f;
                }
            } 
        }
        
        // Scale parameters inversely:
        for (int pidx=0; pidx<parametersInverseInput.get().size(); pidx++) {
            RealParameter param = parametersInverseInput.get().get(pidx);
            for (int i=0; i<param.getDimension(); i++) {
                if (!indicatorsInverseUsed ||
                        indicatorsInverseInput.get().get(pidx).getValue(i)) {
                    double oldValue = param.getValue(i);
                    param.setValue(i, oldValue/f);
                    logHR -= f;
                }
            }
        }
        
        // Return Hastings ratio:
        return logHR;
    }

   
}

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
package beast.math.distributions;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Function;
import beast.core.parameter.BooleanParameter;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.RealParameter;

/**
  * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Just as with Prior, produces log probability of the parameter x. "
        + "This variant however allows one to explicitly exclude individual "
        + "elements of multidimensional parameters from the result.")
public class ExcludablePrior extends Prior {
    
    public Input<BooleanParameter> xIncludeInput = new Input<BooleanParameter>(
            "xInclude", "Array of true/false values specifying which elements"
            + " of x to include", Validate.REQUIRED);
    
    @Override
    public void initAndValidate() throws Exception {        
        super.initAndValidate();
        
        Function x = m_x.get();
        if (x instanceof RealParameter || x instanceof IntegerParameter) {
            if (x.getDimension() != xIncludeInput.get().getDimension())
                throw new IllegalArgumentException("Length of xInclude does "
                        + "not match length of x.");
        }
    }
    
    @Override
    public double calculateLogP() throws Exception {
        Function x = m_x.get();
        if (x instanceof RealParameter || x instanceof IntegerParameter) {
        	// test that parameter is inside its bounds
            double l = 0.0;
            double h = 0.0;
        	if (x instanceof RealParameter) {
                l = ((RealParameter) x).getLower();
                h = ((RealParameter) x).getUpper();
        	} else {
                l = ((IntegerParameter) x).getLower();
                h = ((IntegerParameter) x).getUpper();
        	}
            for (int i = 0; i < x.getDimension(); i++) {
                if (!xIncludeInput.get().getValue(i))
                    continue;
            	double value = x.getArrayValue(i);
            	if (value < l || value > h) {
            		return Double.NEGATIVE_INFINITY;
            	}
            }
        }

        // Inline modified version of ParametricDistribution.calcLogP()        
        final double fOffset = dist.offsetInput.get();
        logP = 0;
        for (int i = 0; i < x.getDimension(); i++) {
            if (!xIncludeInput.get().getValue(i))
                continue;
            final double fX = x.getArrayValue(i) - fOffset;
            logP += dist.logDensity(fX);
        }
        
        return logP;
    }

}

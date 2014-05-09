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
package multitypetree.distributions;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.parameter.RealParameter;
import beast.math.distributions.Prior;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Use when a RealParameter prior has a pole at zero.")
public class PriorWithPole extends Prior {

    public Input<Double> p0Input = new Input<Double>("p0", "Probability "
            + "with which each element takes the value 0.0",
            Validate.REQUIRED);
    
    RealParameter x;
    double p0;
    
    @Override
    public void initAndValidate() throws Exception {

        if (!(m_x.get() instanceof RealParameter))
            throw new RuntimeException("PriorWithPole only applies to"
                    + " RealParameters.");
        
        x = (RealParameter)m_x.get();
        p0 = p0Input.get();
        
        super.initAndValidate();
    }
    
    @Override
    public double calculateLogP() {
        double l = x.getLower();
        double h = x.getUpper();
        
        for (int i=0; i<x.getDimension(); i++) {
            double value = x.getValue(i);
            if (value<l || value>h)
                return Double.NEGATIVE_INFINITY;
        }
        
        final double offset = distInput.get().offsetInput.get();
        logP = 0.0;
        for (int i=0; i<x.getDimension(); i++) {
            double value = x.getValue(i);
            if (value>0) {
                value -= offset;
                logP += Math.log(1.0-p0) + distInput.get().logDensity(value);
            } else {
                logP += Math.log(p0);
            }
        }
        
        return logP;
    }
    
}

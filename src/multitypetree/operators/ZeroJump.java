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
package multitypetree.operators;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Operator;
import beast.core.parameter.RealParameter;
import beast.util.Randomizer;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Reversibly sets a RealParameter element to zero.  Used for"
        + " variable selection.")
public class ZeroJump extends Operator {

    public Input<RealParameter> parameterInput = new Input<>(
            "parameter", "RealParameter to operate on.", Validate.REQUIRED);
    
    public Input<Double> alphaInput = new Input<Double>("mean",
            "Mean of exponential from which non-zero values are drawn in the"
            + " reverse jump.", Validate.REQUIRED);
    
    private RealParameter parameter;
    private double alpha;
    
    @Override
    public void initAndValidate() {
        parameter = parameterInput.get();
        alpha = alphaInput.get();
    }
    
    @Override
    public double proposal() {

        double logHR;
        
        // Select random element:
        int idx = Randomizer.nextInt(parameter.getDimension());
        
        double x = parameter.getValue(idx);
        if (x>0) {
            logHR = Math.log(Math.exp(-x/alpha)/alpha);
            x = 0.0;
        } else {
            x = Randomizer.nextExponential(1.0/alpha);
            logHR = -Math.log(Math.exp(-x/alpha)/alpha);
        }
        
        parameter.setValue(idx, x);
        return logHR;
    }
    
    
    public static void main(String[] args) {
        
        for (int i=0; i<100; i++) {
            System.out.println(Randomizer.nextExponential(10));
        }
    }
}

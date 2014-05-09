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
package multitypetree.util;

import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Logger;
import beast.core.util.ESS;
import beast.evolution.tree.MultiTypeTree;
import beast.math.statistic.DiscreteStatistics;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Special logger for constructing unit tests on multi type tree operator
 * combinations.
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class MultiTypeTreeStatLogger extends Logger {
    
    public Input<MultiTypeTree> multiTypeTreeInput = new Input<MultiTypeTree>(
            "multiTypeTree",
            "Multi-type tree whose stats to log.",
            Validate.REQUIRED);
    
    public Input<Double> burninFracInput = new Input<Double>("burninFrac",
            "Fraction of trace to discard.  Default 0.1.", 0.1);
    
    MultiTypeTree multiTypeTree;
    double burninFrac, logEvery;
    
    List<Double> heights = new ArrayList();
    double [] heightsArray;
    double heightMean, heightVar, heightESS;
    
    @Override
    public void initAndValidate() {
        multiTypeTree = multiTypeTreeInput.get();
        burninFrac = burninFracInput.get();
        logEvery = everyInput.get();
    };

    @Override
    public void init() {
        heights.clear();
    }

    @Override
    public void log(int nSample) {
        
        if ((nSample < 0) || (nSample % logEvery > 0))
            return;
        
        heights.add(multiTypeTree.getRoot().getHeight());
    }

    @Override
    public void close() {
        computeStatistics();
    }

    /**
     * Compute statistics from completed traces.
     */
    public void computeStatistics() {
        
        // Truncate burnin
        heights = heights.subList((int)(burninFrac*heights.size()),
                heights.size()-1);
        
        // Transfer to array for DiscreteStatistics methods:
        heightsArray = new double[heights.size()];
        for (int i=0; i<heights.size(); i++)
            heightsArray[i] = heights.get(i);
        
        // Compute height statistics:
        heightMean = DiscreteStatistics.mean(heightsArray);
        heightVar = DiscreteStatistics.variance(heightsArray);
        heightESS = ESS.calcESS(heights);
    }
    
    public double getHeightMean() {
        return heightMean;
    }
    
    public double getHeightVar() {
        return heightVar;
    }
    
    public double getHeightESS() {
        return heightESS;
    }
    
}

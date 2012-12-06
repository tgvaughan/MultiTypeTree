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
package beast.evolution.alignment;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Sequence annotated with the colour of the taxon.")
public class ColouredSequence extends Sequence {
    public Input<Integer> colourInput = new Input<Integer>("colour",
            "Colour associated with this taxon.", Validate.REQUIRED);
    
    public ColouredSequence() { };
    
    /**
     * Constructor for testing.
     * 
     * @param taxon
     * @param sequence
     * @param colour
     * @throws Exception 
     */
    public ColouredSequence(String taxon, String sequence, int colour) throws Exception {
        m_sTaxon.setValue(taxon, this);
        m_sData.setValue(sequence, this);
        colourInput.setValue(colour, this);
        super.initAndValidate();
    }
}

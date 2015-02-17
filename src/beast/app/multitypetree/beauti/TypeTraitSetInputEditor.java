/*
 * Copyright (C) 2015 Tim Vaughan (tgvaughan@gmail.com)
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
package beast.app.multitypetree.beauti;

import beast.app.beauti.BeautiDoc;
import beast.app.draw.InputEditor;
import beast.core.BEASTInterface;
import beast.core.Input;
import beast.evolution.tree.TraitSet;
import javax.swing.JLabel;

/**
 *
 * @author Tim Vaughan (tgvaughan@gmail.com)
 */
public class TypeTraitSetInputEditor extends InputEditor.Base {

    public TypeTraitSetInputEditor(BeautiDoc doc) {
        super(doc);
    }

    @Override
    public Class<?> type() {
        return TraitSet.class;
    }

    @Override
    public void init(Input<?> input, BEASTInterface plugin, int itemNr, ExpandOption bExpandOption, boolean bAddButtons) {
        // I have no idea what this stuff does:
        m_bAddButtons = bAddButtons;
        m_input = input;
        m_plugin = plugin;
		this.itemNr = itemNr;
        addInputLabel();

        add(new JLabel(input.get().toString()));
    }
    
}

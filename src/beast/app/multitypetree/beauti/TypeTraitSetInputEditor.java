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
import beast.app.draw.ListInputEditor;
import beast.core.BEASTInterface;
import beast.core.Input;
import beast.evolution.tree.MultiTypeTree;
import beast.evolution.tree.TraitSet;
import beast.evolution.tree.Tree;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JLabel;

/**
 *
 * @author Tim Vaughan (tgvaughan@gmail.com)
 */
public class TypeTraitSetInputEditor extends ListInputEditor {

    TraitSet typeTraitSet;

    public TypeTraitSetInputEditor(BeautiDoc doc) {
        super(doc);
    }

    @Override
    public Class<?> type() {
        return List.class;
    }

    @Override
    public Class<?> baseType() {
        return TraitSet.class;
    }

    @Override
    public void init(Input<?> input, BEASTInterface plugin, int itemNr, ExpandOption bExpandOption, boolean bAddButtons) {

        typeTraitSet = null;
        for (TraitSet traitSet : (List<TraitSet>)input.get()) {
            if (traitSet.getTraitName().equals("type")) {
                typeTraitSet = traitSet;
                break;
            }
        }

        if (typeTraitSet == null) {
            throw new IllegalStateException("Template error: no trait with"
                + " name `type' exists.");
        }

        add(new JLabel(typeTraitSet.getID()));

    }
}

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
import beast.evolution.tree.MigrationModel;
import java.awt.Color;
import java.awt.Component;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

/**
 *
 * @author Tim Vaughan (tgvaughan@gmail.com)
 */
public class MigrationModelInputEditor extends InputEditor.Base {

    DefaultTableModel popSizeModel, rateMatrixModel;
    MigrationModel migModel;

    public MigrationModelInputEditor(BeautiDoc doc) {
        super(doc);
    }

    @Override
    public Class<?> type() {
        return MigrationModel.class;
    }

    @Override
    public void init(Input<?> input, BEASTInterface plugin, int itemNr,
        ExpandOption bExpandOption, boolean bAddButtons) {
        m_bAddButtons = bAddButtons;
        m_input = input;
        m_plugin = plugin;
		this.itemNr = itemNr;

        addInputLabel();

        migModel = (MigrationModel) input.get();

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel rowPanel;

        rowPanel = new JPanel();
        rowPanel.add(new JLabel("Number of demes: "));
        JSpinner dimSpinner = new JSpinner(new SpinnerNumberModel(2, 2, Integer.MAX_VALUE, 1));
        dimSpinner.addChangeListener((ChangeEvent e) -> {
            JSpinner spinner = (JSpinner)e.getSource();
            int newDim = (int)spinner.getValue();

            popSizeModel.setColumnCount(newDim);
            migModel.popSizesInput.get().setDimension(newDim);
            rateMatrixModel.setColumnCount(newDim);
            rateMatrixModel.setRowCount(newDim);
            migModel.rateMatrixInput.get().setDimension(newDim*newDim);
            for (int i=0; i<newDim; i++) {
                if (popSizeModel.getValueAt(0, i) == null) {
                    popSizeModel.setValueAt(1.0, 0, i);
                }
                for (int j=0; j<newDim; j++) {
                    if (i==j)
                        continue;
                    if (rateMatrixModel.getValueAt(j, i) == null) {
                        rateMatrixModel.setValueAt(1.0, j, i);
                    }
                }
            }
            saveToMigrationModel();
        });
        rowPanel.add(dimSpinner);
        panel.add(rowPanel);

        popSizeModel = new DefaultTableModel(1, migModel.getNTypes());
        rateMatrixModel = new DefaultTableModel(migModel.getNTypes(), migModel.getNTypes()) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return row != column;
            }
        };
        loadFromMigrationModel();

        popSizeModel.addTableModelListener((TableModelEvent e) -> {
            if (e.getType() != TableModelEvent.UPDATE)
                return;
            
            saveToMigrationModel();
        });
        rateMatrixModel.addTableModelListener((TableModelEvent e) -> {
            if (e.getType() != TableModelEvent.UPDATE)
                return;

            saveToMigrationModel();
        });

        rowPanel = new JPanel();
        rowPanel.add(new JLabel("Population sizes: "));
        JTable popSizeTable = new JTable(popSizeModel);
        popSizeTable.setShowGrid(true);
        popSizeTable.setCellSelectionEnabled(true);
        popSizeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rowPanel.add(popSizeTable);
        panel.add(rowPanel);

        rowPanel = new JPanel();
        rowPanel.add(new JLabel("Migration rates: "));
        JTable rateMatrixTable = new JTable(rateMatrixModel) {
            @Override
            public TableCellRenderer getCellRenderer(int row, int column) {
                if (row != column)
                    return super.getCellRenderer(row, column);
                else
                    return new DefaultTableCellRenderer() {
                        @Override
                        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                            JLabel label = new JLabel();
                            label.setOpaque(true);
                            label.setBackground(Color.GRAY);
                            return label;
                        }
                    };
            }
        };
        rateMatrixTable.setShowGrid(true);
        rateMatrixTable.setCellSelectionEnabled(true);
        rateMatrixTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rowPanel.add(rateMatrixTable);
        panel.add(rowPanel);

        loadFromMigrationModel();

        add(panel);
    }

    public void loadFromMigrationModel() {
        for (int i=0; i<migModel.getNTypes(); i++) {
            popSizeModel.setValueAt(migModel.getPopSize(i), 0, i);
            for (int j=0; j<migModel.getNTypes(); j++) {
                if (i == j)
                    continue;
                rateMatrixModel.setValueAt(migModel.getRate(i, j), i, j);
            }
        }    
    }

    public void saveToMigrationModel() {
        StringBuilder sbPopSize = new StringBuilder();
        for (int i=0; i<popSizeModel.getColumnCount(); i++) {
            if (i>0)
                sbPopSize.append(" ");

            if (popSizeModel.getValueAt(0, i) != null)
                sbPopSize.append(popSizeModel.getValueAt(0, i));
            else
                sbPopSize.append("1.0");
        }
        migModel.popSizesInput.get().setDimension(popSizeModel.getColumnCount());
        migModel.popSizesInput.get().valuesInput.setValue(
            sbPopSize.toString(),
            migModel.popSizesInput.get());

        StringBuilder sbRateMatrix = new StringBuilder();
        boolean first = true;
        for (int i=0; i<rateMatrixModel.getRowCount(); i++) {
            for (int j=0; j<rateMatrixModel.getColumnCount(); j++) {
                if (i == j)
                    continue;

                if (first)
                    first = false;
                else
                    sbRateMatrix.append(" ");

                if (rateMatrixModel.getValueAt(i, j) != null)
                    sbRateMatrix.append(rateMatrixModel.getValueAt(i, j));
                else
                    sbRateMatrix.append("1.0");
            }
        }
        migModel.rateMatrixInput.get().setDimension(
            popSizeModel.getColumnCount()*(popSizeModel.getColumnCount()-1));
        migModel.rateMatrixInput.get().valuesInput.setValue(
            sbRateMatrix.toString(),
            migModel.rateMatrixInput.get());
  }
}

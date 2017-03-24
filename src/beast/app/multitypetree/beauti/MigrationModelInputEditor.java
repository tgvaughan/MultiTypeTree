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
import beast.core.parameter.RealParameter;
import beast.evolution.tree.SCMigrationModel;
import beast.evolution.tree.StructuredCoalescentMultiTypeTree;
import com.sun.org.apache.xml.internal.security.Init;
import multitypetree.distributions.StructuredCoalescentTreeDensity;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * A BEAUti input editor for MigrationModels.
 *
 * @author Tim Vaughan (tgvaughan@gmail.com)
 */
public class MigrationModelInputEditor extends InputEditor.Base {

    DefaultTableModel popSizeModel, rateMatrixModel;
    SpinnerNumberModel nTypesModel;
    SCMigrationModel migModel;

    JCheckBox popSizeEstCheckBox, rateMatrixEstCheckBox;

    boolean dimChangeInProgress = false;

    List<String> rowNames = new ArrayList<>();

    public MigrationModelInputEditor(BeautiDoc doc) {
        super(doc);
    }

    @Override
    public Class<?> type() {
        return SCMigrationModel.class;
    }

    @Override
    public void init(Input<?> input, BEASTInterface beastObject, int itemNr,
        ExpandOption bExpandOption, boolean bAddButtons) {

        // Set up fields
        m_bAddButtons = bAddButtons;
        m_input = input;
        m_beastObject = beastObject;
		this.itemNr = itemNr;

        // Adds label to left of input editor
        addInputLabel();

        // Create component models and fill them with data from input
        migModel = (SCMigrationModel) input.get();
        nTypesModel = new SpinnerNumberModel(2, 2, Short.MAX_VALUE, 1);
        popSizeModel = new DefaultTableModel();
        rateMatrixModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return row != column && column != migModel.getNTypes();
            }
        };
        popSizeEstCheckBox = new JCheckBox("estimate");
        rateMatrixEstCheckBox = new JCheckBox("estimate");
        loadFromMigrationModel();

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EtchedBorder());

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.weighty = 0.5;

        // Deme count spinner:
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0.0;
        c.anchor = GridBagConstraints.LINE_END;
        panel.add(new JLabel("Number of demes: "), c);

        JSpinner dimSpinner = new JSpinner(nTypesModel);
        dimSpinner.setMaximumSize(new Dimension(100, Short.MAX_VALUE));
        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.LINE_START;
        panel.add(dimSpinner, c);

        // Population size table
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0.0;
        c.anchor = GridBagConstraints.LINE_END;
        panel.add(new JLabel("Population sizes: "), c);

        JTable popSizeTable = new JTable(popSizeModel) {
            @Override
            public TableCellRenderer getCellRenderer(int row, int column) {
                return new DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(
                            JTable table, Object value, boolean isSelected,
                            boolean hasFocus, int row, int column) {
                        setHorizontalAlignment(SwingConstants.CENTER);
                        return super.getTableCellRendererComponent(
                                table, value, isSelected, hasFocus, row, column);
                    }
                };
            }
        };
        popSizeTable.setShowVerticalLines(true);
        popSizeTable.setCellSelectionEnabled(true);
        popSizeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        popSizeTable.setMaximumSize(new Dimension(100, Short.MAX_VALUE));

        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.LINE_START;
        panel.add(popSizeTable, c);
        popSizeEstCheckBox.setSelected(((RealParameter)migModel.popSizesInput.get()).isEstimatedInput.get());

        c.gridx = 2;
        c.gridy = 1;
        c.anchor = GridBagConstraints.LINE_END;
        c.weightx = 1.0;
        panel.add(popSizeEstCheckBox, c);

        // Migration rate table
        // (Uses custom cell renderer to grey out diagonal elements.)
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0.0;
        c.anchor = GridBagConstraints.LINE_END;
        panel.add(new JLabel("Migration rates: "), c);
        JTable rateMatrixTable = new JTable(rateMatrixModel) {
            @Override
            public TableCellRenderer getCellRenderer(int row, int column) {

                return new DefaultTableCellRenderer() {
                            @Override
                            public Component getTableCellRendererComponent(
                                    JTable table, Object value, boolean isSelected,
                                    boolean hasFocus, int row, int column) {



                                if (row == column) {
                                    JLabel label = new JLabel();
                                    label.setOpaque(true);
                                    label.setBackground(Color.GRAY);

                                    return label;

                                } else {

                                    Component c = super.getTableCellRendererComponent(
                                        table, value, isSelected, hasFocus, row, column);

                                    JComponent jc = (JComponent)c;
                                    if (column == migModel.getNTypes()) {
                                        c.setBackground(panel.getBackground());
                                        c.setForeground(Color.gray);
                                        setHorizontalAlignment(SwingConstants.LEFT);
                                    } else {
                                        int l = 1, r = 1, t = 1, b=1;
                                        if (column>0)
                                            l = 0;
                                        if (row>0)
                                            t = 0;

                                        setBorder(BorderFactory.createMatteBorder(t, l, b, r, Color.GRAY));
                                        setHorizontalAlignment(SwingConstants.CENTER);
                                    }
                                    return c;
                                }
                            }
                };
            }
        };
        rateMatrixTable.setShowGrid(false);
        rateMatrixTable.setIntercellSpacing(new Dimension(0,0));
        rateMatrixTable.setCellSelectionEnabled(true);
        rateMatrixTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rateMatrixTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        TableColumn col = rateMatrixTable.getColumnModel().getColumn(migModel.getNTypes());


        FontMetrics metrics = new Canvas().getFontMetrics(getFont());
        int maxWidth = 0;
        for (String rowName : rowNames)
            maxWidth = Math.max(maxWidth, metrics.stringWidth(rowName + "M"));

        col.setPreferredWidth(maxWidth);
//        rateMatrixTable.setMaximumSize(new Dimension(100, Short.MAX_VALUE));

        c.gridx = 1;
        c.gridy = 2;
        c.anchor = GridBagConstraints.LINE_START;
        c.weightx = 1.0;
        panel.add(rateMatrixTable, c);

        rateMatrixEstCheckBox.setSelected(((RealParameter)migModel.rateMatrixInput.get()).isEstimatedInput.get());
        c.gridx = 2;
        c.gridy = 2;
        c.anchor = GridBagConstraints.LINE_END;
        c.weightx = 1.0;
        panel.add(rateMatrixEstCheckBox, c);

        c.gridx = 1;
        c.gridy = 3;
        c.anchor = GridBagConstraints.LINE_START;
        c.weightx = 1.0;
        panel.add(new JLabel("Rows: sources, columns: sinks (backwards in time)"), c);

        c.gridx = 1;
        c.gridy = 4;
        c.anchor = GridBagConstraints.LINE_START;
        c.weightx = 1.0;
        JLabel multilineLabel = new JLabel();
        multilineLabel.setText("<html><body>Correspondence between row/col indices<br>"
                + "and deme names shown to right of matrix.</body></html>");
        panel.add(multilineLabel, c);

        add(panel);
 

        // Event handlers

        dimSpinner.addChangeListener((ChangeEvent e) -> {
            JSpinner spinner = (JSpinner)e.getSource();
            int newDim = (int)spinner.getValue();

            dimChangeInProgress = true;

            popSizeModel.setColumnCount(newDim);
            ((RealParameter)migModel.popSizesInput.get()).setDimension(newDim);
            rateMatrixModel.setColumnCount(newDim);
            rateMatrixModel.setRowCount(newDim);
            ((RealParameter)migModel.rateMatrixInput.get()).setDimension(newDim*newDim);
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

            dimChangeInProgress = false;

            saveToMigrationModel();
        });

        popSizeModel.addTableModelListener((TableModelEvent e) -> {
            if (e.getType() != TableModelEvent.UPDATE)
                return;
            
            if (!dimChangeInProgress)
                saveToMigrationModel();
        });

        popSizeEstCheckBox.addItemListener((ItemEvent e) -> {
            saveToMigrationModel();
        });

        rateMatrixModel.addTableModelListener((TableModelEvent e) -> {
            if (e.getType() != TableModelEvent.UPDATE)
                return;

            if (!dimChangeInProgress)
                saveToMigrationModel();
        });

        rateMatrixEstCheckBox.addItemListener((ItemEvent e) -> {
            saveToMigrationModel();
        });
    }

    public void loadFromMigrationModel() {
        nTypesModel.setValue(migModel.getNTypes());
        popSizeModel.setRowCount(1);
        popSizeModel.setColumnCount(migModel.getNTypes());
        rateMatrixModel.setRowCount(migModel.getNTypes());
        rateMatrixModel.setColumnCount(migModel.getNTypes()+1);

        List<String> traitNames = null;
        if (m_beastObject instanceof StructuredCoalescentTreeDensity) {
            StructuredCoalescentTreeDensity scDensity = (StructuredCoalescentTreeDensity)m_beastObject;
            StructuredCoalescentMultiTypeTree scTree = (StructuredCoalescentMultiTypeTree)(scDensity.mtTreeInput.get());
            traitNames = InitMigrationModelConnector.uniqueTraitsInData(scTree);
            nTypesModel.setMinimum(Math.max(traitNames.size(),2));
        };

        rowNames.clear();
        for (int i = 0; i < migModel.getNTypes(); i++) {
        if (traitNames != null && i<traitNames.size())
            rowNames.add(" " + traitNames.get(i) + " (" + String.valueOf(i) + ") ");
        else
            rowNames.add(" (" + String.valueOf(i) + ") ");
        }

        for (int i=0; i<migModel.getNTypes(); i++) {
            popSizeModel.setValueAt(migModel.getPopSize(i), 0, i);
            for (int j=0; j<migModel.getNTypes(); j++) {
                if (i == j)
                    continue;
                rateMatrixModel.setValueAt(migModel.getBackwardRate(i, j), i, j);
            }

            rateMatrixModel.setValueAt(rowNames.get(i), i, migModel.getNTypes());
        }

        popSizeEstCheckBox.setSelected(((RealParameter)migModel.popSizesInput.get()).isEstimatedInput.get());
        rateMatrixEstCheckBox.setSelected(((RealParameter)migModel.rateMatrixInput.get()).isEstimatedInput.get());
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
        ((RealParameter)migModel.popSizesInput.get()).setDimension(popSizeModel.getColumnCount());
        ((RealParameter)migModel.popSizesInput.get()).valuesInput.setValue(
            sbPopSize.toString(),
                (RealParameter)migModel.popSizesInput.get());

        StringBuilder sbRateMatrix = new StringBuilder();
        boolean first = true;
        for (int i=0; i<rateMatrixModel.getRowCount(); i++) {
            for (int j=0; j<rateMatrixModel.getColumnCount()-1; j++) {
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
        ((RealParameter)migModel.rateMatrixInput.get()).setDimension(
            popSizeModel.getColumnCount()*(popSizeModel.getColumnCount()-1));
        ((RealParameter)migModel.rateMatrixInput.get()).valuesInput.setValue(
            sbRateMatrix.toString(),
                (RealParameter)migModel.rateMatrixInput.get());

        ((RealParameter)migModel.popSizesInput.get()).isEstimatedInput.setValue(
            popSizeEstCheckBox.isSelected(), (RealParameter)migModel.popSizesInput.get());
        ((RealParameter)migModel.rateMatrixInput.get()).isEstimatedInput.setValue(
            rateMatrixEstCheckBox.isSelected(), (RealParameter)migModel.rateMatrixInput.get());

        try {
            ((RealParameter)migModel.rateMatrixInput.get()).initAndValidate();
            ((RealParameter)migModel.popSizesInput.get()).initAndValidate();
            migModel.initAndValidate();
        } catch (Exception ex) {
            System.err.println("Error updating migration model state.");
        }

        refreshPanel();
    }
}

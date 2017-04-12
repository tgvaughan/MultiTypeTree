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

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A BEAUti input editor for MigrationModels.
 *
 * @author Tim Vaughan (tgvaughan@gmail.com)
 */
public class MigrationModelInputEditor extends InputEditor.Base {

    DefaultTableModel popSizeModel, rateMatrixModel;
    DefaultListModel<String> fullTypeListModel, additionalTypeListModel;
    ListSelectionModel additionalTypeListSelectionModel;
    SCMigrationModel migModel;

    JButton addTypeButton, remTypeButton, addTypesFromFileButton;
    JButton loadPopSizesFromFileButton, loadMigRatesFromFileButton;

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
        fullTypeListModel = new DefaultListModel<>();
        additionalTypeListModel = new DefaultListModel<>();
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

        // Type list:
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0.0;
        c.anchor = GridBagConstraints.LINE_END;
        panel.add(new JLabel("<html><body>Type list:</body></html>"), c);

        JList<String> jlist;

        Box tlBox = Box.createHorizontalBox();
        Box tlBoxLeft = Box.createVerticalBox();
        JLabel labelLeft = new JLabel("All types");
        tlBoxLeft.add(labelLeft);
        jlist = new JList<>(fullTypeListModel);
        jlist.setSelectionModel(new DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                super.setSelectionInterval(-1, -1);
            }
        });
        JScrollPane listScrollPane = new JScrollPane(jlist);
        listScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        tlBoxLeft.add(listScrollPane);
        tlBox.add(tlBoxLeft);

        Box tlBoxRight = Box.createVerticalBox();
        JLabel labelRight = new JLabel("Additional types");
        tlBoxRight.add(labelRight);
        jlist = new JList<>(additionalTypeListModel);
        additionalTypeListSelectionModel = jlist.getSelectionModel();
        listScrollPane = new JScrollPane(jlist);
        listScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        tlBoxRight.add(listScrollPane);
        Box addRemBox = Box.createHorizontalBox();
        addTypeButton = new JButton("+");
        remTypeButton = new JButton("-");
        remTypeButton.setEnabled(false);
        addTypesFromFileButton = new JButton("Add from file...");
        addRemBox.add(addTypeButton);
        addRemBox.add(remTypeButton);
        addRemBox.add(addTypesFromFileButton);
        tlBoxRight.add(addRemBox);
        tlBox.add(tlBoxRight);

        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.LINE_START;
        panel.add(tlBox, c);

        // Population size table
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0.0;
        c.anchor = GridBagConstraints.LINE_END;
        Box  psBox = Box.createVerticalBox();
        psBox.add(new JLabel("Population sizes: "), c);
        loadPopSizesFromFileButton = new JButton("Load from file...");
        psBox.add(loadPopSizesFromFileButton);
        panel.add(psBox, c);

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
        Box mrBox = Box.createVerticalBox();
        mrBox.add(new JLabel("Migration rates: "), c);
        loadMigRatesFromFileButton = new JButton("Load from file...");
        mrBox.add(loadMigRatesFromFileButton);
        panel.add(mrBox, c);

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

        addTypeButton.addActionListener(e -> {
            String newTypeName = JOptionPane.showInputDialog("Name of type");

            if (newTypeName != null) {
                if (migModel.getTypeSet().containsTypeWithName(newTypeName)) {
                    JOptionPane.showMessageDialog(panel, "Type with this name already present.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    additionalTypeListModel.add(additionalTypeListModel.size(), newTypeName);
                    saveToMigrationModel();
                }
            }
        });

        addTypesFromFileButton.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Choose file containing type names (one per line)");
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fc.setMultiSelectionEnabled(false);
            int result = fc.showDialog(panel, "Load");

            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();

                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));

                    String line = null;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty())
                            additionalTypeListModel.add(additionalTypeListModel.size(), line);
                    }

                    saveToMigrationModel();

                } catch (IOException e1) {
                    JOptionPane.showMessageDialog(panel,
                            "Error reading from file: " + e1.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        additionalTypeListSelectionModel.addListSelectionListener(e -> {
            if (additionalTypeListSelectionModel.getMinSelectionIndex()<0)
                remTypeButton.setEnabled(false);
            else
                remTypeButton.setEnabled(true);
        });

        remTypeButton.addActionListener(e -> {
            int selectionMin = additionalTypeListSelectionModel.getMinSelectionIndex();
            int selectionMax = additionalTypeListSelectionModel.getMaxSelectionIndex();

            additionalTypeListModel.removeRange(selectionMin, selectionMax);

            additionalTypeListSelectionModel.clearSelection();

            saveToMigrationModel();
        });
    }

    public void loadFromMigrationModel() {
        migModel.getTypeSet().initAndValidate();

        additionalTypeListModel.clear();
        if (migModel.getTypeSet().valueInput.get() != null) {
            for (String typeName : migModel.getTypeSet().valueInput.get().split(","))
                if (!typeName.isEmpty())
                    additionalTypeListModel.add(additionalTypeListModel.size(), typeName);
        }

        popSizeModel.setRowCount(1);
        popSizeModel.setColumnCount(migModel.getNTypes());
        rateMatrixModel.setRowCount(migModel.getNTypes());
        rateMatrixModel.setColumnCount(migModel.getNTypes()+1);

        List<String> typeNames = migModel.getTypeSet().getTypesAsList();
        fullTypeListModel.removeAllElements();
        for (String typeName : typeNames)
            fullTypeListModel.add(fullTypeListModel.size(), typeName);

        rowNames.clear();
        for (int i = 0; i < migModel.getNTypes(); i++) {
        if (typeNames != null && i<typeNames.size())
            rowNames.add(" " + typeNames.get(i) + " (" + String.valueOf(i) + ") ");
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

        StringBuilder sbAdditionalTypes = new StringBuilder();
        for (int i=0; i<additionalTypeListModel.size(); i++) {
            if (i > 0)
                sbAdditionalTypes.append(",");
            sbAdditionalTypes.append(additionalTypeListModel.get(i));
        }

        migModel.typeSetInput.get().valueInput.setValue(
                sbAdditionalTypes.toString(),
                migModel.typeSetInput.get());
        migModel.typeSetInput.get().initAndValidate();

        StringBuilder sbPopSize = new StringBuilder();
        for (int i=0; i<migModel.getNTypes(); i++) {
            if (i>0)
                sbPopSize.append(" ");

            if (i < popSizeModel.getColumnCount() && popSizeModel.getValueAt(0, i) != null)
                sbPopSize.append(popSizeModel.getValueAt(0, i));
            else
                sbPopSize.append("1.0");
        }
        ((RealParameter)migModel.popSizesInput.get()).setDimension(migModel.getNTypes());
        ((RealParameter)migModel.popSizesInput.get()).valuesInput.setValue(
            sbPopSize.toString(),
                (RealParameter)migModel.popSizesInput.get());

        StringBuilder sbRateMatrix = new StringBuilder();
        boolean first = true;
        for (int i=0; i<migModel.getNTypes(); i++) {
            for (int j=0; j<migModel.getNTypes(); j++) {
                if (i == j)
                    continue;

                if (first)
                    first = false;
                else
                    sbRateMatrix.append(" ");

                if (i<rateMatrixModel.getRowCount() && j<rateMatrixModel.getColumnCount()-1 && rateMatrixModel.getValueAt(i, j) != null)
                    sbRateMatrix.append(rateMatrixModel.getValueAt(i, j));
                else
                    sbRateMatrix.append("1.0");
            }
        }
        ((RealParameter)migModel.rateMatrixInput.get()).setDimension(
            migModel.getNTypes()*(migModel.getNTypes()-1));
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

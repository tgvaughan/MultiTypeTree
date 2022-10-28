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
package multitypetree.app.beauti;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.inference.parameter.RealParameter;
import beastfx.app.inputeditor.BEASTObjectInputEditor;
import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.util.Alert;
import beastfx.app.util.FXUtils;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import multitypetree.evolution.tree.SCMigrationModel;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A BEAUti input editor for MigrationModels.
 *
 * @author Tim Vaughan (tgvaughan@gmail.com)
 */
public class MigrationModelInputEditor extends BEASTObjectInputEditor { //extends InputEditor.Base {

    private List<TextField> popSizeTFs, rateMatrixTFs;
    private ListView<String> listAllTypes, listAdditional;

    VBox rateMatrixBox;

    private SCMigrationModel migModel;

    private Button addTypeButton, remTypeButton, addTypesFromFileButton;
    private Button loadPopSizesFromFileButton, loadMigRatesFromFileButton;

    private CheckBox popSizeEstCheckBox, popSizeScaleFactorEstCheckBox;
    private CheckBox rateMatrixEstCheckBox, rateMatrixScaleFactorEstCheckBox, rateMatrixForwardTimeCheckBox;

    boolean fileLoadInProgress = false;

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
        ExpandOption isExpandOption, boolean addButtons) {
        // TODO too much works to do if not call super
        super.init(input, beastObject, itemNr, ExpandOption.TRUE, addButtons);

        pane = FXUtils.newHBox();

        // Set up fields
//        m_bAddButtons = addButtons;
//        m_input = input;
//        m_beastObject = beastObject;
//		this.itemNr = itemNr;

        // Adds label to left of input editor
        addInputLabel();

        // Create component models and fill them with data from input
        migModel = (SCMigrationModel) input.get();
//        ObservableList<String> fullTypeList = FXCollections.observableArrayList();
//        ObservableList<String> additionalTypeList = FXCollections.observableArrayList();
//        popSizeModel =  popSizeTable.getSelectionModel();
//        rateMatrixModel = new DefaultTableModel() {
//            @Override
//            public boolean isCellEditable(int row, int column) {
//                return row != column && column != migModel.getNTypes();
//            }
//        };

        popSizeEstCheckBox = new CheckBox("estimate pop. sizes");
        rateMatrixEstCheckBox = new CheckBox("estimate mig. rates");
        popSizeScaleFactorEstCheckBox = new CheckBox("estimate scale factor");
        rateMatrixScaleFactorEstCheckBox = new CheckBox("estimate scale factor");
        rateMatrixForwardTimeCheckBox = new CheckBox("forward-time rate matrix");


        Text first = new Text("Type list:");
        first.setStyle("-fx-font-weight: bold");
//        Label label = new Label("<html><body>Type list:</body></html>");
//        label.setPadding(new Insets(3, 3, 3, 3));

        GridPane gridPane = new GridPane();
        gridPane.setVgap(10);
//        panel.setBorder(new EtchedBorder());
//        VBox box4All = FXUtils.newVBox();

//        HBox tlBox = FXUtils.newHBox();
//        tlBox.getChildren().add(label);

        VBox tlBoxLeft = FXUtils.newVBox();
        Label labelLeft = new Label("All types");
        tlBoxLeft.getChildren().add(labelLeft);
        listAllTypes = new ListView<>();
        listAllTypes.setPrefSize(200, 250);
        listAllTypes.setOrientation(Orientation.VERTICAL);
        listAllTypes.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
//        jlist.setSelectionModel(new DefaultListSelectionModel() {
//            @Override
//            public void setSelectionInterval(int index0, int index1) {
//                super.setSelectionInterval(-1, -1);
//            }
//        });
        ScrollPane listScrollPane = new ScrollPane(listAllTypes);
        listScrollPane.vbarPolicyProperty().setValue(ScrollPane.ScrollBarPolicy.ALWAYS);
        tlBoxLeft.getChildren().add(listScrollPane);

        VBox tlBoxRight = FXUtils.newVBox();
        Label labelRight = new Label("Additional types");
        tlBoxRight.getChildren().add(labelRight);
        listAdditional = new ListView<>();
        listAdditional.setPrefSize(200, 200);
        listAdditional.setOrientation(Orientation.VERTICAL);
        listAdditional.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

//        listAdditional.getItems().addListener((ListChangeListener) change -> {
//            if (listAdditional.getSelectionModel().isEmpty())
//                remTypeButton.setDisable(true);
//            else
//                remTypeButton.setDisable(false);
//        });

        MultipleSelectionModel<String> additionalTypeListSelectionModel = listAdditional.getSelectionModel();
        additionalTypeListSelectionModel.selectedItemProperty().addListener(e -> {
            if (additionalTypeListSelectionModel.isEmpty())
                remTypeButton.setDisable(true);
            else
                remTypeButton.setDisable(false);
        });

        listScrollPane = new ScrollPane(listAdditional);
        listScrollPane.vbarPolicyProperty().setValue(ScrollPane.ScrollBarPolicy.ALWAYS);
        tlBoxRight.getChildren().add(listScrollPane);
        HBox addRemBox = FXUtils.newHBox();
        addTypeButton = new Button("+");
        remTypeButton = new Button("-");
        remTypeButton.setDisable(true);
        addTypesFromFileButton = new Button("Add from file...");
        addRemBox.getChildren().add(addTypeButton);
        addRemBox.getChildren().add(remTypeButton);
        addRemBox.getChildren().add(addTypesFromFileButton);
        tlBoxRight.getChildren().add(addRemBox);

//        tlBox.getChildren().add(tlBoxLeft);
//        tlBox.getChildren().add(tlBoxRight);
        gridPane.add(first, 0, 0, 1, 1);
        gridPane.add(tlBoxLeft, 1, 0, 1, 1);
        gridPane.add(tlBoxRight, 2, 0, 1, 1);

//        c.gridx = 1;
//        c.gridy = 0;
//        c.weightx = 1.0;
//        c.anchor = GridBagConstraints.LINE_START;
//        panel.add(tlBox, 1, 0);
//        box4All.getChildren().add(gridPane);
        // Population size table
//        c.gridx = 0;
//        c.gridy = 1;
//        c.weightx = 0.0;
//        c.anchor = GridBagConstraints.LINE_END;
        VBox  psBox = FXUtils.newVBox();
        psBox.getChildren().add(new Label("Population sizes: "));
        loadPopSizesFromFileButton = new Button("Load from file...");
        psBox.getChildren().add(loadPopSizesFromFileButton);
//        panel.add(psBox, 0, 1);
//        box4All.getChildren().add(psBox);
        gridPane.add(psBox, 0, 1, 1, 1);

//        TableView<double[]> popSizeTable = new TableView<>();
//        popSizeTable.setEditable(true);
//        popSizeTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
//
//        popSize = FXCollections.observableArrayList();//TODO
//        popSize.add(new double[]{1.0, 1.0});
//        // add columns
//        for (int i = 0; i < popSize.size(); i++) {
//            TableColumn<double[], Double> column = new TableColumn<>();
//            popSizeTable.getColumns().add(column);
//        }
//        // add data
//        popSizeTable.setItems(popSize);

//        Pane header = (Pane) popSizeTable.lookup("TableHeaderRow");
//        header.setVisible(false);
//        popSizeTable.setLayoutY(-header.getHeight());
//        popSizeTable.autosize();


//            @Override
//            public TableCellRenderer getCellRenderer(int row, int column) {
//                return new DefaultTableCellRenderer() {
//                    @Override
//                    public Component getTableCellRendererComponent(
//                            JTable table, Object value, boolean isSelected,
//                            boolean hasFocus, int row, int column) {
//                        setHorizontalAlignment(SwingConstants.CENTER);
//                        return super.getTableCellRendererComponent(
//                                table, value, isSelected, hasFocus, row, column);
//                    }
//                };
//            }

//        popSizeTable.setShowVerticalLines(true);
//        popSizeTable.setCellSelectionEnabled(true);
//        popSizeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//        popSizeTable.setMaximumSize(new Dimension(100, Short.MAX_VALUE));

//        c.gridx = 1;
//        c.gridy = 1;
//        c.weightx = 1.0;
//        c.anchor = GridBagConstraints.LINE_START;
//        panel.add(popSizeTable, 1, 1);
//        box4All.getChildren().add(popSizeTable);

//        rateMatrix = FXCollections.observableArrayList();
//        for (int i=0; i < migModel.getNTypes(); i++) {
//            double[] rate1row = new double[migModel.getNTypes()];
//            for (int j=0; j < migModel.getNTypes(); j++) {
//                if (i == j)
//                    continue;
//
//                rate1row[j] = migModel.getBackwardRate(i, j);
//            }
//            rateMatrix.add(rate1row);
//        }

        loadFromMigrationModel(); //TODO

        HBox hb = new HBox();
        hb.getChildren().addAll(popSizeTFs);

        gridPane.add(hb, 1, 1, 2, 1);

        popSizeEstCheckBox.setSelected(((RealParameter)migModel.popSizesInput.get()).isEstimatedInput.get());
        popSizeScaleFactorEstCheckBox.setSelected(((RealParameter)migModel.popSizesScaleFactorInput.get()).isEstimatedInput.get());
//        c.gridx = 2;
//        c.gridy = 1;
//        c.anchor = GridBagConstraints.LINE_END;
//        c.weightx = 1.0;
        VBox estBox = FXUtils.newVBox();
        estBox.getChildren().add(popSizeEstCheckBox);
        estBox.getChildren().add(popSizeScaleFactorEstCheckBox);
//        panel.add(estBox, 2, 1);
//        box4All.getChildren().add(estBox);
        gridPane.add(estBox, 3, 1, 1, 1);

        // Migration rate table
        // (Uses custom cell renderer to grey out diagonal elements.)
//        c.gridx = 0;
//        c.gridy = 2;
//        c.weightx = 0.0;
//        c.anchor = GridBagConstraints.LINE_END;
        VBox mrBox = FXUtils.newVBox();
        mrBox.getChildren().add(new Label("Migration rates: "));
        loadMigRatesFromFileButton = new Button("Load from file...");
        mrBox.getChildren().add(loadMigRatesFromFileButton);
//        panel.add(mrBox, 0, 2);
//        box4All.getChildren().add(mrBox);
        gridPane.add(mrBox, 0, 2, 1, 1);

//        TableView<double[]> rateMatrixTable = new TableView<>();
//        rateMatrixTable.setEditable(true);
//        rateMatrixTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
//        header = (Pane) rateMatrixTable.lookup("TableHeaderRow");
//        header.setVisible(false);
//        rateMatrixTable.setLayoutY(-header.getHeight());
//        rateMatrixTable.autosize();

        // add columns
//        for (int i = 0; i < rateMatrix.size(); i++) {
//            TableColumn<double[], Double> column = new TableColumn<>();
//            rateMatrixTable.getColumns().add(column);
//
//            // add data
//            rateMatrixTable.getItems().add(rateMatrix.get(i));
//        }

//        loadFromMigrationModel();

//            @Override
//            public TableCellRenderer getCellRenderer(int row, int column) {
//
//                return new DefaultTableCellRenderer() {
//                            @Override
//                            public Component getTableCellRendererComponent(
//                                    JTable table, Object value, boolean isSelected,
//                                    boolean hasFocus, int row, int column) {
//
//                                if (row == column) {
//                                    Label label = new Label();
//                                    label.setOpaque(true);
//                                    label.setBackground(Color.GRAY);
//
//                                    return label;
//
//                                } else {
//
//                                    Component c = super.getTableCellRendererComponent(
//                                        table, value, isSelected, hasFocus, row, column);
//
//                                    if (column == migModel.getNTypes()) {
//                                        c.setBackground(panel.getBackground());
//                                        c.setForeground(Color.gray);
//                                        setHorizontalAlignment(SwingConstants.LEFT);
//                                    } else {
//                                        int l = 1, r = 1, t = 1, b=1;
//                                        if (column>0)
//                                            l = 0;
//                                        if (row>0)
//                                            t = 0;
//
//                                        setBorder(BorderFactory.createMatteBorder(t, l, b, r, Color.GRAY));
//                                        setHorizontalAlignment(SwingConstants.CENTER);
//                                    }
//                                    return c;
//                                }
//                            }
//                };
//            }
//
//        rateMatrixTable.setShowGrid(false);
//        rateMatrixTable.setIntercellSpacing(new Dimension(0,0));
//        rateMatrixTable.setCellSelectionEnabled(true);
//        rateMatrixTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//        rateMatrixTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
//        TableColumn col = rateMatrixTable.getColumnModel().getColumn(migModel.getNTypes());
//
//        FontMetrics metrics = new Canvas().getFontMetrics(getFont());
//        int maxWidth = 0;
//        for (String rowName : rowNames)
//            maxWidth = Math.max(maxWidth, metrics.stringWidth(rowName + "M"));
//        col.setPreferredWidth(maxWidth);

//        c.gridx = 1;
//        c.gridy = 2;
//        c.anchor = GridBagConstraints.LINE_START;
//        c.weightx = 1.0;
//        panel.add(rateMatrixTable, 1, 2);
//        box4All.getChildren().add(rateMatrixTable);

        rateMatrixBox = drawRateMatrixBox();
        gridPane.add(rateMatrixBox, 1, 2, 2, 1);

        rateMatrixEstCheckBox.setSelected(((RealParameter)migModel.rateMatrixInput.get()).isEstimatedInput.get());
        rateMatrixScaleFactorEstCheckBox.setSelected(((RealParameter)migModel.rateMatrixScaleFactorInput.get()).isEstimatedInput.get());
        rateMatrixForwardTimeCheckBox.setSelected(migModel.useForwardMigrationRatesInput.get());
//        c.gridx = 2;
//        c.gridy = 2;
//        c.anchor = GridBagConstraints.LINE_END;
//        c.weightx = 1.0;
        estBox = FXUtils.newVBox();
        estBox.getChildren().add(rateMatrixEstCheckBox);
        estBox.getChildren().add(rateMatrixScaleFactorEstCheckBox);
        estBox.getChildren().add(rateMatrixForwardTimeCheckBox);
//        panel.add(estBox, 2,2);
//        box4All.getChildren().add(estBox);
        gridPane.add(estBox, 3, 2, 1, 1);

//        c.gridx = 1;
//        c.gridy = 3;
//        c.anchor = GridBagConstraints.LINE_START;
//        c.weightx = 1.0;
        Text second = new Text("Rows: sources, columns: sinks (backwards in time)");
//        panel.add(l, 1,3);
//        box4All.getChildren().add(l);
        gridPane.add(second, 1, 3, 2, 1);

//        c.gridx = 1;
//        c.gridy = 4;
//        c.anchor = GridBagConstraints.LINE_START;
//        c.weightx = 1.0;
        Label multilineLabel = new Label("Correspondence between row/col indices"
                + "and deme names shown to right of matrix.");
        multilineLabel.setWrapText(true);
        multilineLabel.setMaxWidth(500);
//        panel.add(multilineLabel, 1, 4);
//        box4All.getChildren().add(multilineLabel);
        gridPane.add(multilineLabel, 1, 4, 2, 1);

//        box4All.setBorder(new Border(new BorderStroke(Color.LIGHTGRAY, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(1))));
//        HBox.setHgrow(box4All, Priority.ALWAYS);
//        box4All.getChildren().add(gridPane);

        pane.getChildren().add(gridPane);
        getChildren().add(pane);
//        pane.getChildren().add(panel);
//        m_expansionBox = box;


        // Event handlers
//        popSizeTable.addTableModelListener(e -> {
//            if (e.getType() != TableModelEvent.UPDATE)
//                return;
//
//            if (!fileLoadInProgress)
//                saveToMigrationModel();
//        });

        popSizeEstCheckBox.selectedProperty().addListener(e -> saveToMigrationModel());

        popSizeScaleFactorEstCheckBox.selectedProperty().addListener(e -> saveToMigrationModel());

//        rateMatrixModel.addTableModelListener(e -> {
//            if (e.getType() != TableModelEvent.UPDATE)
//                return;
//
//            if (!fileLoadInProgress)
//                saveToMigrationModel();
//        });

        rateMatrixEstCheckBox.selectedProperty().addListener(e -> saveToMigrationModel());

        rateMatrixScaleFactorEstCheckBox.selectedProperty().addListener(e -> saveToMigrationModel());

        rateMatrixForwardTimeCheckBox.selectedProperty().addListener(e -> saveToMigrationModel());

        addTypeButton.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Name of type");
            dialog.showAndWait().ifPresent(newTypeName -> {
                if (migModel.getTypeSet().containsTypeWithName(newTypeName)) {
                    Alert.showMessageDialog(pane,
                            "Type with this name already present.",
                            "Error",
                            Alert.ERROR_MESSAGE);
                } else {
                    listAdditional.getItems().add(listAdditional.getItems().size(), newTypeName);
                    saveToMigrationModel();
                    loadFromMigrationModel();
                    rateMatrixBox = drawRateMatrixBox();
                    refreshPanel();
                }
            });
        });

        addTypesFromFileButton.setOnAction(e -> {
            File file = FXUtils.getLoadFile("Choose file containing type names (one per line)");
            if (file != null) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty())
                            listAdditional.getItems().add(listAdditional.getItems().size(), line);
                    }

                    saveToMigrationModel();
//TODO
                } catch (IOException e1) {
                    Alert.showMessageDialog(pane,
                            "<html>Error reading from file:<br>" + e1.getMessage() + "</html>",
                            "Error",
                            Alert.ERROR_MESSAGE);
                }
            }
        });


        remTypeButton.setOnAction(e -> {
            List selectedItems = listAdditional.getSelectionModel().getSelectedItems().sorted();
            listAdditional.getItems().removeAll(selectedItems);
            //TODO why not working?
            listAllTypes.getItems().removeAll(selectedItems);

            listAdditional.getSelectionModel().clearSelection();

            saveToMigrationModel();
        });

        loadPopSizesFromFileButton.setOnAction(e -> {
            File file = FXUtils.getLoadFile("Choose file containing population sizes (one per line)");
            if (file != null) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));

                    List<Double> popSizes = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty())
                            popSizes.add(Double.parseDouble(line));
                    }

                    if (popSizes.size() == migModel.getNTypes()) {
                        fileLoadInProgress = true;

                        RealParameter popSizesParam = (RealParameter) migModel.popSizesInput.get();
                        if (popSizeTFs == null)
                            popSizeTFs = new ArrayList<>();
                        else
                            popSizeTFs.clear();
                        for (int i = 0; i < popSizes.size(); i++) {
                            TextField tf = createATextFieldFrom1DArray(popSizesParam, i, popSizes.get(i), "");
                            popSizeTFs.add(tf);
                        }

                        fileLoadInProgress = false;

                        saveToMigrationModel();
                    } else {
                        Alert.showMessageDialog(pane,
                                "<html>File must contain exactly one population<br> size for each type/deme.</html>",
                                "Error",
                                Alert.ERROR_MESSAGE);
                    }

                } catch (IOException ex) {
                    Alert.showMessageDialog(pane,
                            "<html>Error reading from file:<br>" + ex.getMessage() + "</html>",
                            "Error",
                            Alert.ERROR_MESSAGE);
                } catch (NumberFormatException ex) {
                    Alert.showMessageDialog(pane,
                            "<html>File contains non-numeric line. Every line must contain<br> exactly one population size.</html>",
                            "Error",
                            Alert.ERROR_MESSAGE);
                }
            }
        });

        loadMigRatesFromFileButton.setOnAction(e -> {
            File file = FXUtils.getLoadFile("Choose CSV file containing migration rate matrix (diagonal ignored)");
            if (file != null) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));

                    List<Double> migRates = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        for (String field : line.split(",")) {
                            if (!field.isEmpty())
                                migRates.add(Double.parseDouble(field));
                        }
                    }

                    boolean diagonalsPresent = (migRates.size() == migModel.getNTypes()*migModel.getNTypes());
                    if (diagonalsPresent || migRates.size() == migModel.getNTypes()*(migModel.getNTypes()-1)) {

                        fileLoadInProgress = true;

                        RealParameter rateMatrixParam = (RealParameter) migModel.rateMatrixInput.get();

                        if (rateMatrixTFs == null)
                            rateMatrixTFs = new ArrayList<>();
                        else
                            rateMatrixTFs.clear();

                        for (int i=0; i < migModel.getNTypes(); i++) {
                            for (int j=0; j < migModel.getNTypes(); j++) {
                                if (i == j)
                                    continue;
                                // work out index for flattened array
                                int idx = getRateMatrixIndex(i, j, migModel.getNTypes(), diagonalsPresent);
                                TextField tf = createATextFieldFrom1DArray(rateMatrixParam, idx, migRates.get(idx), "");
                                rateMatrixTFs.add(tf);
                            }
                        }

                        fileLoadInProgress = false;

                        saveToMigrationModel();
                    } else {
                        Alert.showMessageDialog(pane,
                                "<html>CSV file must contain a square matrix with exactly one<br>" +
                                        "row for each type/deme.</html>", "Error",
                                Alert.ERROR_MESSAGE);
                    }
                } catch (IOException ex) {
                    Alert.showMessageDialog(pane,
                            "<html>Error reading from file:<br>" + ex.getMessage() + "</html>",
                            "Error", Alert.ERROR_MESSAGE);
                } catch (NumberFormatException ex) {
                    Alert.showMessageDialog(pane,
                            "<html>CSV file contains non-numeric element.</html>", "Error",
                            Alert.ERROR_MESSAGE);
                }
            }
        });
    }

    private VBox drawRateMatrixBox() {
        VBox colBox = new VBox();
        // 2-d text fields
        for (int i=0; i < migModel.getNTypes(); i++) {
            HBox rowBox = new HBox();
            for (int j=0; j < migModel.getNTypes(); j++) {
                if (i == j) {
                    TextField tf = new TextField();
                    tf.setDisable(true);
                    tf.setPrefWidth(70);
                    tf.setBackground(new Background(new BackgroundFill(Color.DARKGREY, CornerRadii.EMPTY, Insets.EMPTY)));
                    rowBox.getChildren().add(tf);
                    continue;
                }
                int idx = getRateMatrixIndex(i, j, migModel.getNTypes(), false);
                rowBox.getChildren().add(rateMatrixTFs.get(idx));
            }
            colBox.getChildren().add(rowBox);
        }
        return colBox;
    }

    private void loadFromMigrationModel() {
        migModel.getTypeSet().initAndValidate();

        listAdditional.getItems().clear();
        if (migModel.getTypeSet().valueInput.get() != null) {
            for (String typeName : migModel.getTypeSet().valueInput.get().split(","))
                if (!typeName.isEmpty())
                    listAdditional.getItems().add(listAdditional.getItems().size(), typeName);
        }

//        popSizeModel.setRowCount(1);
//        popSizeModel.setColumnCount(migModel.getNTypes());
//        rateMatrixModel.setRowCount(migModel.getNTypes());
//        rateMatrixModel.setColumnCount(migModel.getNTypes()+1);

        List<String> typeNames = migModel.getTypeSet().getTypesAsList();
        listAllTypes.getItems().clear();
        for (String typeName : typeNames)
            listAllTypes.getItems().add(listAllTypes.getItems().size(), typeName);

        rowNames.clear();
        for (int i = 0; i < migModel.getNTypes(); i++) {
        if (i < typeNames.size())
            rowNames.add(" " + typeNames.get(i) + " (" + String.valueOf(i) + ") ");
        else
            rowNames.add(" (" + String.valueOf(i) + ") ");
        }

        fillinPopSizeTextFields();
        fillinRateMatrixTextFields();

//        for (int i=0; i<migModel.getNTypes(); i++) {
            //TODO add Label
//            rateMatrixModel.setValueAt(rowNames.get(i), i, migModel.getNTypes());
//        }

        popSizeEstCheckBox.setSelected(((RealParameter) migModel.popSizesInput.get()).isEstimatedInput.get());
        rateMatrixEstCheckBox.setSelected(((RealParameter)migModel.rateMatrixInput.get()).isEstimatedInput.get());
        rateMatrixForwardTimeCheckBox.setSelected(migModel.useForwardMigrationRatesInput.get());
    }

    private void saveToMigrationModel() {

        StringBuilder sbAdditionalTypes = new StringBuilder();
        for (int i = 0; i< listAdditional.getItems().size(); i++) {
            if (i > 0)
                sbAdditionalTypes.append(",");
            sbAdditionalTypes.append(listAdditional.getItems().get(i));
        }

        migModel.typeSetInput.get().valueInput.setValue(
                sbAdditionalTypes.toString(),
                migModel.typeSetInput.get());
        migModel.typeSetInput.get().initAndValidate();

        StringBuilder sbPopSize = new StringBuilder();
        for (int i=0; i<migModel.getNTypes(); i++) {
            if (i>0)
                sbPopSize.append(" ");

            if (i < popSizeTFs.size() && popSizeTFs.get(i) != null) {
                sbPopSize.append(popSizeTFs.get(i).getText());
            } else
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

//                if (i<rateMatrixModel.getRowCount() && j<rateMatrixModel.getColumnCount()-1 && rateMatrixModel.getValueAt(i, j) != null)
                int idx = getRateMatrixIndex(i, j, migModel.getNTypes(), false);
                if (idx < rateMatrixTFs.size() && rateMatrixTFs.get(idx) != null)
                    sbRateMatrix.append(rateMatrixTFs.get(idx).getText());
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
        ((RealParameter)migModel.popSizesScaleFactorInput.get()).isEstimatedInput.setValue(
                popSizeScaleFactorEstCheckBox.isSelected(), (RealParameter)migModel.popSizesScaleFactorInput.get());
        ((RealParameter)migModel.rateMatrixInput.get()).isEstimatedInput.setValue(
            rateMatrixEstCheckBox.isSelected(), (RealParameter)migModel.rateMatrixInput.get());
        ((RealParameter)migModel.rateMatrixScaleFactorInput.get()).isEstimatedInput.setValue(
                rateMatrixScaleFactorEstCheckBox.isSelected(), (RealParameter)migModel.rateMatrixScaleFactorInput.get());
        migModel.useForwardMigrationRatesInput.setValue(
                rateMatrixForwardTimeCheckBox.isSelected(), migModel);

        try {
            ((RealParameter)migModel.rateMatrixInput.get()).initAndValidate();
            ((RealParameter)migModel.popSizesInput.get()).initAndValidate();
            migModel.initAndValidate();
        } catch (Exception ex) {
            System.err.println("Error updating migration model state.");
        }

        refreshPanel();
    }

    private void fillinPopSizeTextFields() {
        RealParameter popSizesParam = (RealParameter) migModel.popSizesInput.get();

        if (popSizeTFs == null)
            popSizeTFs = new ArrayList<>();
        else
            popSizeTFs.clear();
        for (int i = 0; i < migModel.getNTypes(); i++) {
            // popSize dim == getNTypes()
            TextField tf = createATextFieldFrom1DArray(popSizesParam, i, migModel.getPopSize(i), "");
            popSizeTFs.add(tf);
        }
    }

    private int getRateMatrixIndex(int i, int j, int nTypes, boolean diagonalsPresent) {
        int idx;
        if (diagonalsPresent)
            idx = i*nTypes + j;
        else {
            idx = i * (nTypes - 1) + j;
            if (j>i)
                idx -= 1;
        }
//        System.out.println("i = " + i + ", j = " + j + ", idx = " + idx);
        return idx;
    }

    private void fillinRateMatrixTextFields() {
        RealParameter rateMatrixParam = (RealParameter) migModel.rateMatrixInput.get();

        if (rateMatrixTFs == null)
            rateMatrixTFs = new ArrayList<>();
        else
            rateMatrixTFs.clear();

        for (int i=0; i < migModel.getNTypes(); i++) {
            for (int j=0; j < migModel.getNTypes(); j++) {
                if (i == j)
                    continue;
                // work out index for flattened array
                int idx = getRateMatrixIndex(i, j, migModel.getNTypes(), false);
                TextField tf = createATextFieldFrom1DArray(rateMatrixParam, idx, migModel.getBackwardRate(i, j), "");
                rateMatrixTFs.add(tf);
            }
            // move the line to add row names to diff place
        }
    }
    private TextField createATextFieldFrom1DArray(RealParameter param, int index, double val, String toopTip) {

        TextField tf = new TextField();
        tf.setText("" + val);
        tf.setTooltip(new Tooltip(toopTip));
        tf.setPrefWidth(70);

        tf.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!oldValue.equals(newValue)) {
                //System.out.println("textfield changed from " + oldValue + " to " + newValue);
                try {
                    double x = Double.parseDouble(newValue);
                    param.setValue(index, x);
                }catch(Exception e) {

                }
                //saveToBDMM();
            }
        });

        return tf;
    }

    private double getDouble(String val) {
        double x;
        try {
            x = Double.parseDouble(val);
        }catch(NumberFormatException e) {
            x = 0;
        }
        return x;
    }



}

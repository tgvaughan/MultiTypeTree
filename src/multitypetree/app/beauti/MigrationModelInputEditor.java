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
 *  *
 * This class is i the process of being ported into FX. It is not being used anywhere.
 * 
 *
 *
 * @author Tim Vaughan (tgvaughan@gmail.com)
 */
public class MigrationModelInputEditor extends BEASTObjectInputEditor { //extends InputEditor.Base

    private List<TextField> popSizeTFs, rateMatrixTFs;
    private ListView<String> listAllTypes, listAdditional;

    GridPane gridPane;
    HBox popSizesBox;
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
        //TODO the arrow not appear in Mac, if not call super
        super.init(input, beastObject, itemNr, ExpandOption.TRUE, addButtons);

        // Set up fields
//        m_bAddButtons = addButtons;
//        m_input = input;
//        m_beastObject = beastObject;
//		this.itemNr = itemNr;

        pane.getChildren().clear();
        pane = FXUtils.newHBox();

        // Adds label to left of input editor
        addInputLabel();

        // Create component models and fill them with data from input
        migModel = (SCMigrationModel) input.get();

        popSizeEstCheckBox = new CheckBox("estimate pop. sizes");
        rateMatrixEstCheckBox = new CheckBox("estimate mig. rates");
        popSizeScaleFactorEstCheckBox = new CheckBox("estimate scale factor");
        rateMatrixScaleFactorEstCheckBox = new CheckBox("estimate scale factor");
        rateMatrixForwardTimeCheckBox = new CheckBox("forward-time rate matrix");

        Text first = new Text("Type list:");
        first.setStyle("-fx-font-weight: bold");

        gridPane = new GridPane();
        gridPane.setVgap(10);
        gridPane.setStyle("-fx-border-base: gray;" +
                "    -fx-border-shadow: lightgray;" +
                "    -fx-light-border: derive(-fx-border-base, 25%);" +
                "    -fx-border-color: -fx-light-border -fx-border-base -fx-border-base -fx-light-border;" +
                "    -fx-background-color: -fx-border-shadow, -fx-background;" +
                "    -fx-background-insets: 1 0 0 1, 2;" +
                "    -fx-padding: 2;");
//        panel.setBorder(new EtchedBorder());

        VBox tlBoxLeft = FXUtils.newVBox();
        Label labelLeft = new Label("All types");
        tlBoxLeft.getChildren().add(labelLeft);
        listAllTypes = new ListView<>();
        listAllTypes.setPrefSize(200, 250);
        listAllTypes.setOrientation(Orientation.VERTICAL);
        listAllTypes.setMouseTransparent( true );
        listAllTypes.setFocusTraversable( false );
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

        gridPane.add(first, 0, 0, 1, 1);
        gridPane.add(tlBoxLeft, 1, 0, 1, 1);
        gridPane.add(tlBoxRight, 2, 0, 1, 1);

        // Population size table
        VBox  psBox = FXUtils.newVBox();
        psBox.getChildren().add(new Label("Population sizes: "));
        loadPopSizesFromFileButton = new Button("Load from file...");
        psBox.getChildren().add(loadPopSizesFromFileButton);
        gridPane.add(psBox, 0, 1, 1, 1);

        loadFromMigrationModel(); //TODO

        popSizesBox = new HBox();
        popSizesBox.getChildren().addAll(popSizeTFs);
        gridPane.add(popSizesBox, 1, 1, 2, 1);

        popSizeEstCheckBox.setSelected(((RealParameter)migModel.popSizesInput.get()).isEstimatedInput.get());
        popSizeScaleFactorEstCheckBox.setSelected(((RealParameter)migModel.popSizesScaleFactorInput.get()).isEstimatedInput.get());

        VBox estBox = FXUtils.newVBox();
        estBox.getChildren().add(popSizeEstCheckBox);
        estBox.getChildren().add(popSizeScaleFactorEstCheckBox);
        gridPane.add(estBox, 3, 1, 1, 1);

        // Migration rate table
        VBox mrBox = FXUtils.newVBox();
        mrBox.getChildren().add(new Label("Migration rates: "));
        loadMigRatesFromFileButton = new Button("Load from file...");
        mrBox.getChildren().add(loadMigRatesFromFileButton);
        gridPane.add(mrBox, 0, 2, 1, 1);

//        FontMetrics metrics = new Canvas().getFontMetrics(getFont());
//        int maxWidth = 0;
//        for (String rowName : rowNames)
//            maxWidth = Math.max(maxWidth, metrics.stringWidth(rowName + "M"));
//        col.setPreferredWidth(maxWidth);

        rateMatrixBox = drawRateMatrixBox();
        gridPane.add(rateMatrixBox, 1, 2, 2, 1);

        rateMatrixEstCheckBox.setSelected(((RealParameter)migModel.rateMatrixInput.get()).isEstimatedInput.get());
        rateMatrixScaleFactorEstCheckBox.setSelected(((RealParameter)migModel.rateMatrixScaleFactorInput.get()).isEstimatedInput.get());
        rateMatrixForwardTimeCheckBox.setSelected(migModel.useForwardMigrationRatesInput.get());

        estBox = FXUtils.newVBox();
        estBox.getChildren().add(rateMatrixEstCheckBox);
        estBox.getChildren().add(rateMatrixScaleFactorEstCheckBox);
        estBox.getChildren().add(rateMatrixForwardTimeCheckBox);
        gridPane.add(estBox, 3, 2, 1, 1);

        Text second = new Text("Rows: sources, columns: sinks (backwards in time)");
        gridPane.add(second, 1, 3, 2, 1);

        Label multilineLabel = new Label("Correspondence between row/col indices"
                + "and deme names shown to right of matrix.");
        multilineLabel.setWrapText(true);
        multilineLabel.setMaxWidth(500);
        gridPane.add(multilineLabel, 1, 4, 2, 1);

        pane.getChildren().add(gridPane);
        getChildren().add(pane);

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
                    refreshPopSizeAndRateMatrixGUI();
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

                    refreshPopSizeAndRateMatrixGUI();
                } catch (IOException e1) {
                    Alert.showMessageDialog(pane,
                            "<html>Error reading from file:<br>" + e1.getMessage() + "</html>",
                            "Error",
                            Alert.ERROR_MESSAGE);
                }
            }
        });


        remTypeButton.setOnAction(e -> {
            final ObservableList<String> selectedItems = listAdditional.getSelectionModel().getSelectedItems();
            listAdditional.getItems().removeAll(selectedItems);

            listAdditional.getSelectionModel().clearSelection();

            List<String> typeNames = migModel.getTypeSet().getTypesAsList();
            listAllTypes.getItems().clear();
            for (String typeName : typeNames)
                listAllTypes.getItems().add(listAllTypes.getItems().size(), typeName);

            refreshPopSizeAndRateMatrixGUI();
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

                        refreshPopSizeAndRateMatrixGUI();
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

                        refreshPopSizeAndRateMatrixGUI();
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

    private void refreshPopSizeAndRateMatrixGUI() {
        saveToMigrationModel();
        loadFromMigrationModel();

        gridPane.getChildren().remove(popSizesBox);
        popSizesBox = new HBox();
        popSizesBox.getChildren().addAll(popSizeTFs);
        gridPane.add(popSizesBox, 1, 1, 2, 1);

        gridPane.getChildren().remove(rateMatrixBox);
        rateMatrixBox = drawRateMatrixBox();
        gridPane.add(rateMatrixBox, 1, 2, 2, 1);
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
            Label rn = new Label(rowNames.get(i));
            rowBox.getChildren().add(rn);
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

        List<String> typeNames = migModel.getTypeSet().getTypesAsList();
        listAllTypes.getItems().clear();
        for (String typeName : typeNames)
            listAllTypes.getItems().add(listAllTypes.getItems().size(), typeName);

        rowNames.clear();
        for (int i = 0; i < migModel.getNTypes(); i++) {
        if (i < typeNames.size())
            rowNames.add(" " + typeNames.get(i) + " (" + i + ") ");
        else
            rowNames.add(" (" + i + ") ");
        }

        fillinPopSizeTextFields();
        fillinRateMatrixTextFields();

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

    private void  fillinRateMatrixTextFields() {
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

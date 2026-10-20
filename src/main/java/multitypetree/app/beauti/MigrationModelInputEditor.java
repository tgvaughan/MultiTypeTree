/*
 * Copyright (C) 2025 ETH Zürich
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
import beastfx.app.inputeditor.InputEditor;
import beastfx.app.util.Alert;
import beastfx.app.util.FXUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import multitypetree.evolution.tree.SCMigrationModel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A BEAUti input editor for MigrationModels.
 *
 * @author Tim Vaughan (tgvaughan@gmail.com)
 * @author Ugne Stolz
 */
public class MigrationModelInputEditor extends BEASTObjectInputEditor {

    // UI components for migration model editing
    private ListView<String> listAllTypes, listAdditional;
    private List<TextField> popSizeTFs, rateMatrixTFs;
    private Button addTypeButton, remTypeButton, addTypesFromFileButton;
    private Button loadPopSizesFromFileButton, loadMigRatesFromFileButton;
    private CheckBox popSizeEstCheckBox, popSizeScaleFactorEstCheckBox;
    private CheckBox rateMatrixEstCheckBox, rateMatrixScaleFactorEstCheckBox, rateMatrixForwardTimeCheckBox;
    private boolean fileLoadInProgress = false;
    private List<String> rowNames = new ArrayList<>();

    private SCMigrationModel migModel;

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

        super.init(input, beastObject, itemNr, ExpandOption.TRUE, bAddButtons);


        if (this.pane != null) {
            // get here when refreshing
            pane.getChildren().clear();
        } else {
            this.pane = FXUtils.newHBox();
            getChildren().add(pane);
        }

        addInputLabel();

        // Initialize fields and models
        migModel = (SCMigrationModel) input.get();
        popSizeTFs = new ArrayList<>();
        rateMatrixTFs = new ArrayList<>();
        listAllTypes = new ListView<>();
        listAdditional = new ListView<>();

        // configure list views for "All types" and "Additional types"
        listAllTypes.setPrefSize(200, 250);
        listAllTypes.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);  // No selection needed, but allow multiple for consistency
        listAdditional.setPrefSize(200, 200);
        listAdditional.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        // disable the "Remove type" button initially (no selection)
        remTypeButton = new Button("-");
        remTypeButton.setDisable(true);

        // other buttons for the type list
        addTypeButton = new Button("+");
        addTypesFromFileButton = new Button("Add from file...");

        // buttons for loading pop sizes and mig rates from file
        loadPopSizesFromFileButton = new Button("Load from file...");
        loadMigRatesFromFileButton = new Button("Load from file...");

        // checkboxes
        popSizeEstCheckBox = new CheckBox("estimate pop. sizes");
        popSizeScaleFactorEstCheckBox = new CheckBox("estimate scale factor");
        rateMatrixEstCheckBox = new CheckBox("estimate mig. rates");
        rateMatrixScaleFactorEstCheckBox = new CheckBox("estimate scale factor");
        rateMatrixForwardTimeCheckBox = new CheckBox("forward-time rate matrix");

        // layout
        GridPane gridPane = new GridPane();
        gridPane.setVgap(10);

        // top row: type list label and the two lists side by side:
        // 1. all types
        // 2. additional types
        Text titleLabel = new Text("Type list:");
        titleLabel.setStyle("-fx-font-weight: bold");
        Label labelLeft = new Label("All types");
        Label labelRight = new Label("Additional types");

        // always show scroll bars
        ScrollPane allTypesScroll = new ScrollPane(listAllTypes);
        allTypesScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        ScrollPane additionalScroll = new ScrollPane(listAdditional);
        additionalScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        // "All types" in a vertical box with label
        VBox tlBoxLeft = FXUtils.newVBox();
        tlBoxLeft.getChildren().add(labelLeft);
        tlBoxLeft.getChildren().add(allTypesScroll);
        // "Additional types" in a vertical box with label and buttons
        VBox tlBoxRight = FXUtils.newVBox();
        tlBoxRight.getChildren().add(labelRight);
        tlBoxRight.getChildren().add(additionalScroll);
        HBox addRemBox = FXUtils.newHBox();
        addRemBox.getChildren().add(addTypeButton);
        addRemBox.getChildren().add(remTypeButton);
        addRemBox.getChildren().add(addTypesFromFileButton);
        tlBoxRight.getChildren().add(addRemBox);
        // title and the two list boxes to the grid
        gridPane.add(titleLabel, 0, 0);
        gridPane.add(tlBoxLeft, 1, 0);
        gridPane.add(tlBoxRight, 2, 0);

        // second row: pop sizes label + file button (left), pop size fields (center), estimate checkboxes (right)
        VBox psBox = FXUtils.newVBox();
        psBox.getChildren().add(new Label("Population sizes: "));
        psBox.getChildren().add(loadPopSizesFromFileButton);
        gridPane.add(psBox, 0, 1);

        // third row: mig rates label + file button (left), mig matrix grid (center), estimate checkboxes (right)
        VBox mrBox = FXUtils.newVBox();
        mrBox.getChildren().add(new Label("Migration rates: "));
        mrBox.getChildren().add(loadMigRatesFromFileButton);
        gridPane.add(mrBox, 0, 2);

        // load current values from the migration model
        loadFromMigrationModel();

        // popSizeTFs and rateMatrixTFs are filled. Need to add them to the layout
        // add pop size text fields in one horizontal row
        HBox popSizeRow = new HBox();
        popSizeRow.getChildren().addAll(popSizeTFs);
        gridPane.add(popSizeRow, 1, 1, 2, 1);
        // pop size estimate checkboxes on the right
        VBox popEstBox = FXUtils.newVBox();
        popEstBox.getChildren().add(popSizeEstCheckBox);
        popEstBox.getChildren().add(popSizeScaleFactorEstCheckBox);
        gridPane.add(popEstBox, 3, 1);

        // mig rate matrix fields
        VBox rateMatrixBox = drawRateMatrixBox();
        gridPane.add(rateMatrixBox, 1, 2, 2, 1);
        // mig rates checkboxes on the right
        VBox rateEstBox = FXUtils.newVBox();
        rateEstBox.getChildren().add(rateMatrixEstCheckBox);
        rateEstBox.getChildren().add(rateMatrixScaleFactorEstCheckBox);
        rateEstBox.getChildren().add(rateMatrixForwardTimeCheckBox);
        gridPane.add(rateEstBox, 3, 2);

        // labels for the matrix
        Text legend1 = new Text("Rows: sources, columns: sinks (backwards in time)");
        Label legend2 = new Label("Correspondence between row/col indices\nand deme names shown to right of matrix.");
        legend2.setWrapText(true);
        legend2.setMaxWidth(500);
        gridPane.add(legend1, 1, 3, 2, 1);
        gridPane.add(legend2, 1, 4, 2, 1);

        pane.getChildren().add(gridPane);


        // Enable/disable the remove-type button based on selection in the additional types list
        listAdditional.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (listAdditional.getSelectionModel().isEmpty()) {
                remTypeButton.setDisable(true);
            } else {
                remTypeButton.setDisable(false);
            }
        });

        // save changes when op size checkbox is selected
        popSizeEstCheckBox.setOnAction(e -> saveToMigrationModel());
        popSizeScaleFactorEstCheckBox.setOnAction(e -> saveToMigrationModel());
        rateMatrixEstCheckBox.setOnAction(e -> saveToMigrationModel());
        rateMatrixScaleFactorEstCheckBox.setOnAction(e -> saveToMigrationModel());
        rateMatrixForwardTimeCheckBox.setOnAction(e -> saveToMigrationModel());

        // adding new type: prompt for name and add to additional types list
        addTypeButton.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Name of type");
            dialog.setHeaderText(null);
            dialog.setContentText("Enter new type name:");
            dialog.showAndWait().ifPresent(newTypeName -> {
                if (migModel.getTypeSet().containsTypeWithName(newTypeName)) {
                    // show error if a type with this name already exists
                    Alert.showMessageDialog(pane, "Type with this name already present.", "Error", Alert.ERROR_MESSAGE);
                } else {
                    listAdditional.getItems().add(newTypeName);
                    saveToMigrationModel();  // update model and refresh UI
                }
            });
        });

        // add multiple types from file
        addTypesFromFileButton.setOnAction(e -> {
            File file = FXUtils.getLoadFile("Choose file containing type names (one per line)");
            if (file != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            listAdditional.getItems().add(line);
                        }
                    }
                    saveToMigrationModel();  // save and refresh after adding all types
                } catch (IOException ex) {
                    Alert.showMessageDialog(pane, "Error reading from file: " + ex.getMessage(), "Error", Alert.ERROR_MESSAGE);
                }
            }
        });

        // remove selected types from additional types list
        remTypeButton.setOnAction(e -> {
            List<String> selected = new ArrayList<>(listAdditional.getSelectionModel().getSelectedItems());
            listAdditional.getItems().removeAll(selected);
            listAdditional.getSelectionModel().clearSelection();
            saveToMigrationModel();
        });

        // load pop sizes from a file (one size per line)
        loadPopSizesFromFileButton.setOnAction(e -> {
            File file = FXUtils.getLoadFile("Choose file containing population sizes (one per line)");
            if (file != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                    List<Double> popSizes = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            popSizes.add(Double.parseDouble(line));
                        }
                    }
                    if (popSizes.size() == migModel.getNTypes()) {
                        fileLoadInProgress = true;
                        for (int i = 0; i < popSizes.size(); i++) {
                            if (i < popSizeTFs.size()) {
                                popSizeTFs.get(i).setText(popSizes.get(i).toString());
                            }
                        }
                        fileLoadInProgress = false;
                        saveToMigrationModel();
                    } else {
                        Alert.showMessageDialog(pane,
                                "File must contain exactly one population size for each type/deme.",
                                "Error", Alert.ERROR_MESSAGE);
                    }
                } catch (IOException ex) {
                    Alert.showMessageDialog(pane, "Error reading from file: " + ex.getMessage(), "Error", Alert.ERROR_MESSAGE);
                } catch (NumberFormatException ex) {
                    Alert.showMessageDialog(pane,
                            "File contains non-numeric line. Every line must contain exactly one population size.",
                            "Error", Alert.ERROR_MESSAGE);
                }
            }
        });

        // Load migration rate matrix from a CSV file
        loadMigRatesFromFileButton.setOnAction(e -> {
            File file = FXUtils.getLoadFile("Choose CSV file containing migration rate matrix (diagonal ignored)");
            if (file != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                    List<Double> migRates = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        for (String field : line.split(",")) {
                            if (!field.isEmpty())
                                migRates.add(Double.parseDouble(field));
                        }
                    }
                    int n = migModel.getNTypes();
                    boolean diagonalsPresent = (migRates.size() == n * n);
                    if (diagonalsPresent || migRates.size() == n * (n - 1)) {
                        fileLoadInProgress = true;
                        // each off-diagonal value from file into the appropriate text field
                        for (int i = 0; i < n; i++) {
                            for (int j = 0; j < n; j++) {
                                if (i == j) continue;
                                int fileIndex = getRateMatrixIndex(i, j, n, diagonalsPresent);
                                int fieldIndex = getRateMatrixIndex(i, j, n, false);
                                if (fileIndex < migRates.size() && fieldIndex < rateMatrixTFs.size()) {
                                    rateMatrixTFs.get(fieldIndex).setText(migRates.get(fileIndex).toString());
                                }
                            }
                        }
                        fileLoadInProgress = false;
                        saveToMigrationModel();
                    } else {
                        Alert.showMessageDialog(pane,
                                "CSV file must contain a square matrix with exactly one row for each type/deme.",
                                "Error", Alert.ERROR_MESSAGE);
                    }
                } catch (IOException ex) {
                    Alert.showMessageDialog(pane, "Error reading from file: " + ex.getMessage(), "Error", Alert.ERROR_MESSAGE);
                } catch (NumberFormatException ex) {
                    Alert.showMessageDialog(pane, "CSV file contains non-numeric element.", "Error", Alert.ERROR_MESSAGE);
                }
            }
        });
    }

    private void loadFromMigrationModel() {
        migModel.getTypeSet().initAndValidate();

        listAdditional.getItems().clear();
        String typeCSV = migModel.getTypeSet().valueInput.get();
        if (typeCSV != null) {
            for (String typeName : typeCSV.split(",")) {
                if (!typeName.isEmpty()) {
                    listAdditional.getItems().add(typeName);
                }
            }
        }

        List<String> typeNames = migModel.getTypeSet().getTypesAsList();
        listAllTypes.getItems().clear();
        for (String typeName : typeNames) {
            listAllTypes.getItems().add(typeName);
        }

        rowNames.clear();
        for (int i = 0; i < migModel.getNTypes(); i++) {
            if (i < typeNames.size()) {
                // Include type name if available, with index
                rowNames.add(" " + typeNames.get(i) + " (" + i + ") ");
            } else {
                // Fallback for index beyond provided names
                rowNames.add(" (" + i + ") ");
            }
        }

        popSizeTFs.clear();
        RealParameter popSizesParam = (RealParameter) migModel.popSizesInput.get();
        for (int i = 0; i < migModel.getNTypes(); i++) {
            double popSizeVal = migModel.getPopSize(i);
            // Create a TextField for this population size value
            TextField tf = createParameterTextField(popSizesParam, i, popSizeVal, "");
            popSizeTFs.add(tf);
        }

        rateMatrixTFs.clear();
        RealParameter rateParam = (RealParameter) migModel.rateMatrixInput.get();
        int n = migModel.getNTypes();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;  // skip diagonal
                // Compute the index in the flat (N*(N-1)) parameter for this rate
                int idx = getRateMatrixIndex(i, j, n, false);
                double rateVal = migModel.getBackwardRate(i, j);
                TextField tf = createParameterTextField(rateParam, idx, rateVal, "");
                rateMatrixTFs.add(tf);
            }
        }

        // initial states of checkboxes
        popSizeEstCheckBox.setSelected(((RealParameter) migModel.popSizesInput.get()).isEstimatedInput.get());
        popSizeScaleFactorEstCheckBox.setSelected(((RealParameter) migModel.popSizesScaleFactorInput.get()).isEstimatedInput.get());
        rateMatrixEstCheckBox.setSelected(((RealParameter) migModel.rateMatrixInput.get()).isEstimatedInput.get());
        rateMatrixScaleFactorEstCheckBox.setSelected(((RealParameter) migModel.rateMatrixScaleFactorInput.get()).isEstimatedInput.get());
        rateMatrixForwardTimeCheckBox.setSelected(migModel.useForwardMigrationRatesInput.get());
    }

    private void saveToMigrationModel() {
        StringBuilder sbAdditional = new StringBuilder();
        for (int i = 0; i < listAdditional.getItems().size(); i++) {
            if (i > 0) sbAdditional.append(",");
            sbAdditional.append(listAdditional.getItems().get(i));
        }
        migModel.typeSetInput.get().valueInput.setValue(sbAdditional.toString(), migModel.typeSetInput.get());
        migModel.typeSetInput.get().initAndValidate();

        // update pop size
        StringBuilder sbPopSizes = new StringBuilder();
        for (int i = 0; i < migModel.getNTypes(); i++) {
            if (i > 0) sbPopSizes.append(" ");
            if (i < popSizeTFs.size() && popSizeTFs.get(i) != null) {
                sbPopSizes.append(popSizeTFs.get(i).getText());
            } else {
                sbPopSizes.append("1.0");
            }
        }
        RealParameter popSizesParam = (RealParameter) migModel.popSizesInput.get();
        popSizesParam.setDimension(migModel.getNTypes());
        popSizesParam.valuesInput.setValue(sbPopSizes.toString(), popSizesParam);

        // update rateMatrix
        StringBuilder sbRates = new StringBuilder();
        boolean first = true;
        int n = migModel.getNTypes();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                if (!first) {
                    sbRates.append(" ");
                } else {
                    first = false;
                }
                int idx = getRateMatrixIndex(i, j, n, false);
                if (idx < rateMatrixTFs.size() && rateMatrixTFs.get(idx) != null) {
                    sbRates.append(rateMatrixTFs.get(idx).getText());
                } else {
                    sbRates.append("1.0");
                }
            }
        }
        RealParameter rateParam = (RealParameter) migModel.rateMatrixInput.get();
        // dim: # of off-diagonal entries
        rateParam.setDimension(n * (n - 1));
        rateParam.valuesInput.setValue(sbRates.toString(), rateParam);

        // update flags
        ((RealParameter) migModel.popSizesInput.get()).isEstimatedInput.setValue(popSizeEstCheckBox.isSelected(), popSizesParam);
        ((RealParameter) migModel.popSizesScaleFactorInput.get()).isEstimatedInput.setValue(popSizeScaleFactorEstCheckBox.isSelected(), (RealParameter) migModel.popSizesScaleFactorInput.get());
        rateParam.isEstimatedInput.setValue(rateMatrixEstCheckBox.isSelected(), rateParam);
        ((RealParameter) migModel.rateMatrixScaleFactorInput.get()).isEstimatedInput.setValue(rateMatrixScaleFactorEstCheckBox.isSelected(), (RealParameter) migModel.rateMatrixScaleFactorInput.get());
        migModel.useForwardMigrationRatesInput.setValue(rateMatrixForwardTimeCheckBox.isSelected(), migModel);

        try {
            rateParam.initAndValidate();
            popSizesParam.initAndValidate();
            migModel.initAndValidate();
        } catch (Exception ex) {
            System.err.println("Error updating migration model state.");
        }

        refreshPanel();
        Platform.runLater(() -> init(m_input, m_beastObject, itemNr, ExpandOption.TRUE, m_bAddButtons));
        sync();
    }

    /** Constructs the migration rate matrix UI (text fields for rates and labels for row names). */
    private VBox drawRateMatrixBox() {
        VBox matrixColumn = new VBox();
        int n = migModel.getNTypes();
        // each row of the matrix is an HBox
        for (int i = 0; i < n; i++) {
            HBox rowBox = new HBox();
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    // diag elements: greyed out to indicate no self-migration
                    TextField diagField = new TextField();
                    diagField.setDisable(true);
                    diagField.setPrefWidth(70);
                    diagField.setBackground(new Background(new BackgroundFill(Color.DARKGRAY, CornerRadii.EMPTY, Insets.EMPTY)));
                    rowBox.getChildren().add(diagField);
                } else {
                    // off-diag: use the corresponding TextField from rateMatrixTFs list
                    int idx = getRateMatrixIndex(i, j, n, false);
                    if (idx < rateMatrixTFs.size()) {
                        rowBox.getChildren().add(rateMatrixTFs.get(idx));
                    }
                }
            }
            // label at the end of the row with the type name and index
            Label rowLabel = new Label(rowNames.get(i));
            rowLabel.setTextFill(Color.GRAY);
            // add a small left margin
            HBox.setMargin(rowLabel, new Insets(0, 0, 0, 5));
            rowBox.getChildren().add(rowLabel);
            matrixColumn.getChildren().add(rowBox);
        }
        return matrixColumn;
    }

    /**
     * Compute the index in a flattened (one-dimensional) array for the migration rate element (i,j).
     * This matches how the RealParameter for rate matrix is structured (off-diagonals only).
     * @param i  row index (source deme)
     * @param j  column index (destination deme)
     * @param nTypes total number of types (demes)
     * @param diagonalsPresent whether the index should be computed as if diagonal elements are present in the array
     * @return index in the flat array corresponding to rate[i][j]
     */
    private int getRateMatrixIndex(int i, int j, int nTypes, boolean diagonalsPresent) {
        if (i == j) {
            throw new IllegalArgumentException("Diagonal elements have no index in rate matrix array.");
        }
        int idx;
        if (diagonalsPresent) {
            // if diagonal entries are considered
            idx = i * nTypes + j;
        } else {
            // no diagonals in array
            idx = i * (nTypes - 1) + j;
            if (j > i) {
                idx -= 1;
            }
        }
        return idx;
    }

    private TextField createParameterTextField(RealParameter param, int index, double value, String tooltip) {
        TextField tf = new TextField(Double.toString(value));
        tf.setPrefWidth(70);
        if (!tooltip.isEmpty()) {
            tf.setTooltip(new Tooltip(tooltip));
        }

        // Live update of in‐memory parameter
        tf.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!fileLoadInProgress && !oldVal.equals(newVal)) {
                try {
                    param.setValue(index, Double.parseDouble(newVal));
                } catch (NumberFormatException ex) { /* ignore */ }
            }
        });

        // On Enter, push into the model inputs & re‐serialize
        tf.setOnAction(e -> {
            try {
                saveToMigrationModel();
            } catch (Exception ex) {
                Alert.showMessageDialog(pane,
                        "Failed to save pop-size/migration-rate value: " + ex.getMessage(),
                        "Error", Alert.ERROR_MESSAGE);
            }
        });

        // On focus lost, same commit
        tf.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused) {
                try {
                    saveToMigrationModel();
                } catch (Exception ex) {
                    Alert.showMessageDialog(pane,
                            "Failed to save pop-size/migration-rate value: " + ex.getMessage(),
                            "Error", Alert.ERROR_MESSAGE);
                }
            }
        });

        return tf;
    }

}

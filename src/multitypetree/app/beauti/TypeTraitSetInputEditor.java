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
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.TraitSet;
import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.inputeditor.GuessPatternDialog;
import beastfx.app.inputeditor.InputEditor;
import beastfx.app.util.FXUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * BEAUti input editor for MultiTypeTree type traits.
 *
 * @author Tim Vaughan (tgvaughan@gmail.com)
 * @author Walter Xie
 * @author Jordan Douglas
 */
public class TypeTraitSetInputEditor extends InputEditor.Base {

//    TypeTraitTableModel tableModel;
    private ObservableList<Location> locations;
    TraitSet traitSet;
    TaxonSet taxonSet;
    TableView<Location> table;

    public TypeTraitSetInputEditor(BeautiDoc doc) {
        super(doc);

    }

    @Override
    public Class<?> type() {
        return TraitSet.class;
    }

    @Override
    public void init(Input<?> input, BEASTInterface plugin, int itemNr, ExpandOption bExpandOption, boolean bAddButtons) {

    	
    	
    	locations = FXCollections.observableArrayList();
    	
        traitSet = (TraitSet)input.get();
        taxonSet = traitSet.taxaInput.get();
//        tableModel = new TypeTraitTableModel(traitSet);
        table = new TableView<>();
        table.setEditable(true);
        table.setPrefWidth(1024);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setItems(locations);
        
        TableColumn<Location, String> column1 = new TableColumn<>("Name");
        column1.setCellValueFactory(new PropertyValueFactory<>("name"));
        column1.setPrefWidth(500);
        column1.setEditable(false);

        TableColumn<Location, String> column2 =new TableColumn<>("Location");
        column2.setPrefWidth(500);
        column2.setEditable(true);
        column2.setCellValueFactory(new PropertyValueFactory<>("location"));
        column2.setCellFactory(TextFieldTableCell.forTableColumn());
        
        column2.setOnEditCommit(
                t -> t.getTableView().getItems().
                        get(t.getTablePosition().getRow()).
                        setLocation(t.getNewValue())
        );

        
        table.getColumns().add(column1);
        table.getColumns().add(column2);

        traitSetToLocations();

        Button guessButton = new Button("Guess");
        guessButton.setOnAction(e -> {
            GuessPatternDialog dlg = new GuessPatternDialog(null,
                ".*(\\d\\d\\d\\d).*");
            
            String traitString = "";
            switch(dlg.showDialog("Guess locations")) {
                case canceled:
                    return;
                case trait:
                    traitString = dlg.getTrait();
                    break;
                case pattern:
                    StringBuilder traitStringBuilder = new StringBuilder();
                    for (String taxonName : taxonSet.asStringList()) {
                        String matchString = dlg.match(taxonName);
                        if (matchString == null)
                            return;
                        
                        if (traitStringBuilder.length()>0)
                            traitStringBuilder.append(",");
                        
                        traitStringBuilder.append(taxonName)
                            .append("=")
                            .append(matchString);
                    }
                    traitString = traitStringBuilder.toString();
                    break;
            }
            traitSet.traitsInput.setValue(traitString, traitSet);
            try {
                traitSet.initAndValidate();
            } catch (Exception ex) {
                System.err.println("Error setting type trait.");
            }

            traitSetToLocations();

        });

        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> {
            StringBuilder traitStringBuilder = new StringBuilder();
            for (String taxonName : taxonSet.asStringList()) {
                if (traitStringBuilder.length()>0)
                    traitStringBuilder.append(",");
                traitStringBuilder.append(taxonName).append("=0");
            }
            traitSet.traitsInput.setValue(traitStringBuilder.toString(), traitSet);
            try {
                traitSet.initAndValidate();
            } catch (Exception ex) {
                System.err.println("Error clearing type trait.");
            }

            traitSetToLocations();

        });

        VBox boxVert = FXUtils.newVBox();

        HBox boxHoriz = FXUtils.newHBox();
        boxHoriz.getChildren().add(guessButton);
        boxHoriz.getChildren().add(clearButton);

        boxVert.getChildren().add(boxHoriz);
        boxVert.getChildren().add(table);
        
        this.pane = FXUtils.newHBox();
        getChildren().add(pane);

        pane.getChildren().add(boxVert);
        

        
        
    }
    
    
    private void saveTraitSet() {
        StringBuilder traitStringBuilder = new StringBuilder();
        for (Location loc : locations) {
            if (traitStringBuilder.length()>0) traitStringBuilder.append(",");
            
            String location = loc.getLocation();
            String taxon = loc.getName();
            traitStringBuilder.append(taxon).append("=").append(location);
        }
        traitSet.traitsInput.setValue(traitStringBuilder.toString(), traitSet);
        try {
            traitSet.initAndValidate();
        } catch (Exception ex) {
            System.err.println("Error setting type trait.");
        }
    }

    private void traitSetToLocations() {
        locations.clear();
        // add data
        for (String taxonName : taxonSet.asStringList()) {
            String loc = traitSet.getStringValue(taxonName);
            locations.add(new Location(taxonName, loc));
        }
        
        table.refresh();
        refreshPanel();
    }

    /**
     * Table model
     */
    public class Location {
        String name;
        String location;

        public Location(String name, String location) {
            this.name = name;
            this.location = location;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
            saveTraitSet();
        }
    }

}

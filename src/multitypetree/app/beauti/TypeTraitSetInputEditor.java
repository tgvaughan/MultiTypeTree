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
import javafx.embed.swing.SwingNode;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
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
 */
public class TypeTraitSetInputEditor extends InputEditor.Base {

//    TypeTraitTableModel tableModel;
    private ObservableList<Location> data;
    TraitSet traitSet; // TODO seem to duplicate with ObservableList<Location>
    TaxonSet taxonSet;

    public TypeTraitSetInputEditor(BeautiDoc doc) {
        super(doc);

    }

    @Override
    public Class<?> type() {
        return TraitSet.class;
    }

    @Override
    public void init(Input<?> input, BEASTInterface plugin, int itemNr, ExpandOption bExpandOption, boolean bAddButtons) {

        traitSet = (TraitSet)input.get();
        taxonSet = traitSet.taxaInput.get();
//        tableModel = new TypeTraitTableModel(traitSet);
        TableView<Location> table = new TableView<>();
        TableColumn<Location, String> column1 = new TableColumn<>("Name");
        column1.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<Location, String> column2 =new TableColumn<>("Location");
        column2.setCellValueFactory(new PropertyValueFactory<>("location"));

        column2.setEditable(true);
        column2.setCellFactory(TextFieldTableCell.forTableColumn());
        column2.setOnEditCommit(
                t -> t.getTableView().getItems().
                        get(t.getTablePosition().getRow()).
                        setLocation(t.getNewValue())
                //TODO set traitSet
        );
        table.getColumns().addAll(column1, column2);

        data = FXCollections.observableArrayList();
        table.setItems(data);

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

            for (String taxon : traitSet.taxaInput.get().getTaxaNames()) {
                String loc = traitSet.getStringValue(taxon);
                data.add(new Location(taxon, loc));
            }

            refreshPanel();
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

            for (String taxon : traitSet.taxaInput.get().getTaxaNames()) {
                String loc = traitSet.getStringValue(taxon);
                data.add(new Location(taxon, loc));
            }

            refreshPanel();
        });

        VBox boxVert = FXUtils.newVBox();

        HBox boxHoriz = FXUtils.newHBox();
        boxHoriz.getChildren().add(guessButton);
        boxHoriz.getChildren().add(clearButton);

        boxVert.getChildren().add(boxHoriz);
        boxVert.getChildren().add(new ScrollPane(table));

        this.pane = FXUtils.newHBox();
        getChildren().add(pane);
        
        
        //SwingNode n = new SwingNode();
       // n.setContent(boxVert);
        pane.getChildren().add(boxVert);
        
    }

    /**
     * Table model
     */
    class Location {
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
        }
    }

    /**
    class TypeTraitTableModel extends AbstractTableModel {

        TraitSet typeTraitSet;

        public TypeTraitTableModel(TraitSet typeTraitSet) {
            this.typeTraitSet = typeTraitSet;
        }

        @Override
        public int getRowCount() {
            return typeTraitSet.taxaInput.get().getTaxonCount();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex<0 || columnIndex>=getRowCount())
                return null;

            switch(columnIndex) {
                case 0:
                    // Taxon name:
                    return typeTraitSet.taxaInput.get().getTaxonId(rowIndex);
                case 1:
                    // Type:
                    return typeTraitSet.getStringValue(rowIndex);
                default:
                    return null;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 1;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            String taxon = taxonSet.getTaxonId(rowIndex);
            String traitString = traitSet.traitsInput.get();
            int startIdx = traitString.indexOf(taxon + "=");
            int endIdx = traitString.indexOf(",", startIdx);

            String newTraitString = traitString.substring(0, startIdx);
            newTraitString += taxon + "=" + (String)aValue;
            if (endIdx>=0)
                newTraitString += traitString.substring(endIdx);

            traitSet.traitsInput.setValue(newTraitString, traitSet);
            try {
                traitSet.initAndValidate();
            } catch (Exception ex) {
                System.err.println("Error setting type trait value.");
            }

            fireTableCellUpdated(rowIndex, columnIndex);
        }

        @Override
        public String getColumnName(int column) {
            switch(column) {
                case 0:
                    return "Name";
                case 1:
                    return "Location";
                default:
                    return null;
            }
        }
    }*/
}

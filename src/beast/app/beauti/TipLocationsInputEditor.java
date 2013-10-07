package beast.app.beauti;


import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.EventObject;
import java.util.List;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.CellEditorListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import beast.app.draw.BEASTObjectInputEditor;
import beast.core.Input;
import beast.core.BEASTObject;
import beast.evolution.alignment.Taxon;
import beast.evolution.alignment.TaxonSet;
import beast.evolution.operators.TipDatesRandomWalker;
import beast.evolution.tree.TraitSet;
import beast.evolution.tree.MultiTypeTree;
import beast.evolution.tree.StructuredCoalescentMultiTypeTree;
import java.util.logging.Level;
import java.util.logging.Logger;



public class TipLocationsInputEditor extends BEASTObjectInputEditor {

	public TipLocationsInputEditor(BeautiDoc doc) {
		super(doc);
	}

	private static final long serialVersionUID = 1L;


    @Override
    public Class<?> type() {
        return StructuredCoalescentMultiTypeTree.class;
    }

    StructuredCoalescentMultiTypeTree tree;
    TraitSet traitSet;
    JComboBox unitsComboBox;
    JComboBox relativeToComboBox;
    List<String> sTaxa;
    Object[][] tableData;
    JTable table;
    String m_sPattern = ".*(\\d\\d\\d\\d).*";
    JScrollPane scrollPane;
    List<Taxon> taxonsets;


    @Override
    public void init(Input<?> input, BEASTObject plugin, int itemNr, ExpandOption bExpandOption, boolean bAddButtons) {
        m_bAddButtons = bAddButtons;
		this.itemNr = itemNr;
		if (itemNr >= 0) {
	        tree = (StructuredCoalescentMultiTypeTree) ((List<?>)input.get()).get(itemNr);
		} else {
	        tree = (StructuredCoalescentMultiTypeTree) input.get();			
		}
        if (tree != null) {
            //m_input = tree.typeTraitSetInput;
            m_plugin = tree;
            //traitSet = tree.typeTraitSetInput.get();

            Box box = Box.createVerticalBox();

            if (traitSet == null) {
                traitSet = new TraitSet();
                try {
                    traitSet.initByName("traitname", "discrete",
                                "taxa", tree.m_taxonset.get(),
                                "value", "");
                } catch (Exception ex) {
                    Logger.getLogger(TipLocationsInputEditor.class.getName()).log(Level.SEVERE, null, ex);
                }
                    traitSet.setID("typeTrait.t:" + BeautiDoc.parsePartition(tree.getID()));
                }
            try {
                m_input.setValue(traitSet, m_plugin);
            } catch (Exception ex) {
                Logger.getLogger(TipLocationsInputEditor.class.getName()).log(Level.SEVERE, null, ex);
            }

            Box box2 = Box.createHorizontalBox();
            box2.add(Box.createGlue());
            box.add(box2);

            if (traitSet != null) {
                box.add(createListBox());
            }
            add(box);
        }
    } // init

    final static String ALL_TAXA = "all";


    private Component createListBox() {
        sTaxa = traitSet.taxaInput.get().asStringList();
        String[] columnData = new String[]{"Name", "Location"};
        tableData = new Object[sTaxa.size()][2];
        convertTraitToTableData();
        // set up table.
        // special features: background shading of rows
        // custom editor allowing only Date column to be edited.
        table = new JTable(tableData, columnData) {
            private static final long serialVersionUID = 1L;

            // method that induces table row shading
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                //even index, selected or not selected
                if (isCellSelected(Index_row, Index_col)) {
                    comp.setBackground(Color.lightGray);
                } else if (Index_row % 2 == 0 && !isCellSelected(Index_row, Index_col)) {
                    comp.setBackground(new Color(237, 243, 255));
                } else {
                    comp.setBackground(Color.white);
                }
                return comp;
            }
        };

        // set up editor that makes sure only doubles are accepted as entry
        // and only the Date column is editable.
        table.setDefaultEditor(Object.class, new TableCellEditor() {
            JTextField m_textField = new JTextField();
            int m_iRow
                    ,
                    m_iCol;

            @Override
            public boolean stopCellEditing() {
                table.removeEditor();
                String sText = m_textField.getText();
//                try {
//                    Double.parseDouble(sText);
//                } catch (Exception e) {
//                	try {
//                		Date.parse(sText);
//                	} catch (Exception e2) {
//                        return false;
//					}
//                }
                tableData[m_iRow][m_iCol] = sText;
                convertTableDataToTrait();
                convertTraitToTableData();
                return true;
            }

            @Override
            public boolean isCellEditable(EventObject anEvent) {
                return table.getSelectedColumn() == 1;
            }


            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int iRow, int iCol) {
                if (!isSelected) {
                    return null;
                }
                m_iRow = iRow;
                m_iCol = iCol;
                m_textField.setText((String) value);
                return m_textField;
            }

            @Override
            public boolean shouldSelectCell(EventObject anEvent) {
                return false;
            }

            @Override
            public void removeCellEditorListener(CellEditorListener l) {
            }

            @Override
            public Object getCellEditorValue() {
                return null;
            }

            @Override
            public void cancelCellEditing() {
            }

            @Override
            public void addCellEditorListener(CellEditorListener l) {
            }

        });
        table.setRowHeight(24);
        scrollPane = new JScrollPane(table);
        scrollPane.addComponentListener(new ComponentListener() {
			
			@Override
			public void componentShown(ComponentEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void componentResized(ComponentEvent e) {
				Component c = (Component) e.getSource();
				while (c.getParent() != null && !(c.getParent() instanceof JSplitPane)) {
					c = c.getParent();
				}
				if (c.getParent() != null) {
					Dimension preferredSize = c.getSize();
					preferredSize.height = Math.max(preferredSize.height - 170, 0);
					preferredSize.width = Math.max(preferredSize.width - 25, 0);
					scrollPane.setPreferredSize(preferredSize);					
				} else if (doc.getFrame() != null) {
					Dimension preferredSize = doc.getFrame().getSize();
					preferredSize.height = Math.max(preferredSize.height - 170, 0);
					preferredSize.width = Math.max(preferredSize.width - 25, 0);
					scrollPane.setPreferredSize(preferredSize);
				}				
			}
			
			@Override
			public void componentMoved(ComponentEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void componentHidden(ComponentEvent e) {
				// TODO Auto-generated method stub
				
			}
		});
        
        return scrollPane;
    } // createListBox

    /* synchronise table with data from traitSet Plugin */
    private void convertTraitToTableData() {
        for (int i = 0; i < tableData.length; i++) {
            tableData[i][0] = sTaxa.get(i);
            tableData[i][1] = "0";
        }
        String[] sTraits = traitSet.traitsInput.get().split(",");
        for (String sTrait : sTraits) {
            sTrait = sTrait.replaceAll("\\s+", " ");
            String[] sStrs = sTrait.split("=");
            if (sStrs.length != 2) {
                break;
                //throw new Exception("could not parse trait: " + sTrait);
            }
            String sTaxonID = normalize(sStrs[0]);
            int iTaxon = sTaxa.indexOf(sTaxonID);
//            if (iTaxon < 0) {
//                throw new Exception("Trait (" + sTaxonID + ") is not a known taxon. Spelling error perhaps?");
//            }
            if (iTaxon >= 0) {
	            tableData[iTaxon][1] = normalize(sStrs[1]);
	            tableData[iTaxon][0] = sTaxonID;
            } else {
            	System.err.println("WARNING: File contains taxon " + sTaxonID + " that cannot be found in alignment");
            }
        }

        if (table != null) {
            for (int i = 0; i < tableData.length; i++) {
                table.setValueAt(tableData[i][1], i, 1);
            }
        }
    } // convertTraitToTableData


    private String normalize(String sStr) {
        if (sStr.charAt(0) == ' ') {
            sStr = sStr.substring(1);
        }
        if (sStr.endsWith(" ")) {
            sStr = sStr.substring(0, sStr.length() - 1);
        }
        return sStr;
    }

    /**
     * synchronise traitSet Plugin with table data
     */
    private void convertTableDataToTrait() {
        String sTrait = "";
        for (int i = 0; i < tableData.length; i++) {
            sTrait += sTaxa.get(i) + "=" + tableData[i][1];
            if (i < tableData.length - 1) {
                sTrait += ",\n";
            }
        }
        try {
            traitSet.traitsInput.setValue(sTrait, traitSet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

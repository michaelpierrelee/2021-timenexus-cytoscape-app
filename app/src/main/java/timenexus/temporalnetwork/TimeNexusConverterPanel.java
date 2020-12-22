package timenexus.temporalnetwork;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.text.NumberFormatter;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyTableManager;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.FinishStatus.Type;

import timenexus.utils.ServiceProvider;

import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskObserver;

/*
 * This class displays a window to select the tables among those loaded into Cytoscape
 * and generate a multi-layer network from them. The MLN is built from 3 lists of tables:
 * - tables defining the nodes for each layer;
 * - tables defining the intra-layer edges for each layer;
 * - and the same for inter-layer edges.
 * 
 * For each table, the list of its columns are displayed and the user has to set for each
 * of them their type. For a table, it is mandatory to set the column containing the
 * names of the elements (nodes, edges). For edge tables of a layer, it is possible to
 * either use a column whose the rows have the format "<source> (interacts with) <target>",
 * or use two columns, one for source nodes and another for target nodes.
 * 
 * Nodes which are sources and targets within the tables of intra- and inter-layer edges
 * have to be also defined within the node tables. The reverse is not true, as nodes can be
 * defined within the node tables but they are not necessarily involved within an intra- or
 * inter-layer edge.
 * 
 * By default, it is necessary to set one table for each layer and for each of 3 types of
 * table. However, the parameters "node-aligned network", "edge-aligned network" and "inter
 * -layer coupling is equivalent" allows to use only one Cytoscape table to set all layers.
 * If this option is selected, the user can use one column (such as "Node weight") to set
 * all layers in the same. In other words, that column will be copied into each layer. Yet,
 * it is still possible to set a particular layer if one selects a column type ending by
 * "layer_i" where "i" is the ID of the layer. In this case, the column will be unique to
 * this layer.
 * 
 * When a Cytoscape table is used to set all layers of a given type (node, intra-layer edges,
 * inter-layer edges), then some column types are incompatible: it is not possible to use
 * a column of a given type setting all layers and, in the meanwhile, set the same type of
 * column to set only one layer. Indeed, these column types would be conflicting. 
 * 
 * Moreover, it is not mandatory to define the inter-layer edges. Indeed, the user can
 * select the option "generate inter-layer coupling as diagonal" to do it automatically.
 * The inter-layer edges will be built such as an edge is added to link a node from layer i
 * into a node in layer i+1, if both nodes have the same name. Doing so, the coupling is
 * "diagonal". It involves that if two successive layers do not share any nodes, these layers
 * won't be connected.
 * 
 * The default weights and direction (e.g. if there is a "direction", it means that the edge
 * is "directed"; reciprocally if there is "no direction", the edge is "undirected") allows
 * to set a default weight or direction if these elements are not defined by a column or
 * if some elements of this column are missing (= null elements). For example, if the user
 * did not set a column of weights for a layer i in one of the table, then the weight will be
 * defined according to the default weight of the table type. These parameters are used to
 * build the inter-layer edges when the parameter "inter-layer coupling as diagonal" is
 * selected.
 * 
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class TimeNexusConverterPanel extends AbstractTask {
	
	private SpinnerModel numberLayers = new SpinnerNumberModel( 2, 2, null, 1 );
	private Hashtable<String, CyTable> listCyTables = new Hashtable<String, CyTable>();
	private JCheckBox isNodeAlignedCheck, isEdgeAlignedCheck, isEquivalentCouplingCheck, autoDiagonalCouplingCheck,
		areIntraEdgeDirectedCheck, areInterEdgeDirectedCheck, allNodesAreQueriesCheck;
	
	public TimeNexusConverterPanel() {
		
		CyTableManager serviceTableManager = ServiceProvider.get(CyTableManager.class);
		
		for (CyTable cyTable : serviceTableManager.getAllTables(false))
			listCyTables.put( cyTable.getTitle(), cyTable);
	}

	@Override
	public void run(TaskMonitor taskMonitor) {
		
		/**** Define Frame ****/
		JFrame frame = new JFrame( "Convert Cytoscape networks to multi-layer network" );
		frame.setMinimumSize(new Dimension(800, 800));
		frame.setLocationRelativeTo(null);
		
	    /**** Define form elements ****/
		//		Set number of layers
		JLabel numberLayersLabel = new JLabel( "Number of layers" );
		JSpinner numberLayersSpinner = new JSpinner( numberLayers );
		setComponentSize( numberLayersSpinner, 50, numberLayersSpinner.getMinimumSize().height );
		//		Define formatter for weight fields
		NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(15);
        nf.setMinimumFractionDigits(0);
        NumberFormatter formatter = new NumberFormatter(nf);
        formatter.setMinimum(0.d);
        formatter.setMaximum(1.d);
		//		Set default node's weight
		JLabel defaultNodeWeightLabel = new JLabel( "Default node's weight" );
		JFormattedTextField defaultNodeWeightField = new JFormattedTextField(formatter);
		defaultNodeWeightField.setValue( 1.0d );
		setComponentSize( defaultNodeWeightField, 100, defaultNodeWeightField.getMinimumSize().height );
		//		Set default intra-layer edge's weight
		JLabel defaultIntraEdgeWeightLabel = new JLabel( "Default intra-layer edge's weight" );
		JFormattedTextField defaultIntraEdgeWeightField = new JFormattedTextField(formatter);
		defaultIntraEdgeWeightField.setValue( 1.0d );
		setComponentSize( defaultIntraEdgeWeightField, 100, defaultIntraEdgeWeightField.getMinimumSize().height );
		//		Set default inter-layer edge's weight
		JLabel defaultInterEdgeWeightLabel = new JLabel( "Default inter-layer edge's weight" );
		JFormattedTextField defaultInterEdgeWeightField = new JFormattedTextField(formatter);
		defaultInterEdgeWeightField.setValue( 1.0d );
		setComponentSize( defaultInterEdgeWeightField, 100, defaultInterEdgeWeightField.getMinimumSize().height );
		//		Network parameters
		isNodeAlignedCheck = new JCheckBox("Node-aligned network (same nodes in each layer).");
		isEdgeAlignedCheck = new JCheckBox("Edge-aligned network (same intra-layer edges in each layer).");
		autoDiagonalCouplingCheck = new JCheckBox("Generate automatic inter-layer coupling as diagonal (the same nodes are coupled with their counterparts).");
		isEquivalentCouplingCheck = new JCheckBox("Inter-layer coupling is equivalent for each pair of layers.");
		areIntraEdgeDirectedCheck = new JCheckBox("Intra-layer edges are directed by default (if not checked: undirected).");
		areInterEdgeDirectedCheck = new JCheckBox("Inter-layer edges are directed by default (if not checked: undirected).");
		allNodesAreQueriesCheck = new JCheckBox("All nodes are query nodes.");
		//		Submit
		JButton submit = new JButton( "Convert to multi-layer network" );
	    
	    /**** Define rows of the form ****/
		//		Number of layers
		Box numberLayersBox = defineFormRows( numberLayersLabel, numberLayersSpinner );
		numberLayersBox.setToolTipText("Set the number of layers in the multi-layer networks (min: 2).");
		//		Default weight
		Box defaultNodeWeightBox = defineFormRows( defaultNodeWeightLabel, defaultNodeWeightField );
		Box defaultIntraWeightBox = defineFormRows( defaultIntraEdgeWeightLabel, defaultIntraEdgeWeightField );
		Box defaultInterWeightBox = defineFormRows( defaultInterEdgeWeightLabel, defaultInterEdgeWeightField );
		defaultIntraWeightBox.setToolTipText("Default weight when no column is selected to define this weight.");
		defaultNodeWeightBox.setToolTipText("Default weight when no column is selected to define this weight.");
		defaultInterWeightBox.setToolTipText("Default weight when no column is selected to define this weight.");
		//		Check boxes
		Box isNodeAlignedBox = defineFormRows( new JLabel(), isNodeAlignedCheck );
		Box isEdgeAlignedBox = defineFormRows( new JLabel(), isEdgeAlignedCheck );
		Box autoDiagonalCouplingBox = defineFormRows( new JLabel(), autoDiagonalCouplingCheck );
		Box isEquivalentCouplingBox = defineFormRows( new JLabel(), isEquivalentCouplingCheck );
		Box AreIntraEdgeDirectedBox = defineFormRows( new JLabel(), areIntraEdgeDirectedCheck );
		Box AreInterEdgeDirectedBox = defineFormRows( new JLabel(), areInterEdgeDirectedCheck );
		Box allNodesAreQueriesBox = defineFormRows( new JLabel(), allNodesAreQueriesCheck );
		autoDiagonalCouplingBox.setToolTipText("If a node is present in both layers N and N+1 (= same ID), then the node's counterparts are linked by a directed edge.");
		allNodesAreQueriesBox.setToolTipText("Create columns in the node table for each layer to set all node-layers as query nodes.");
		//		Submit
		Box submitBox = Box.createHorizontalBox();
		submitBox.add( Box.createGlue() );
		submitBox.add( submit );
		submitBox.add( Box.createGlue() );
		
	    /***** Define groups of the form ****/
		//		Parameters
		Box paramGroup = Box.createVerticalBox();
		paramGroup.add( numberLayersBox );
		paramGroup.add( defaultNodeWeightBox );
		paramGroup.add( defaultIntraWeightBox );
		paramGroup.add( defaultInterWeightBox );
		paramGroup.add( isNodeAlignedBox );
		paramGroup.add( isEdgeAlignedBox );
		paramGroup.add( autoDiagonalCouplingBox );
		paramGroup.add( isEquivalentCouplingBox );
		paramGroup.add( AreIntraEdgeDirectedBox );
		paramGroup.add( AreInterEdgeDirectedBox );
		paramGroup.add( allNodesAreQueriesBox );
		paramGroup.setBorder( BorderFactory.createTitledBorder( "Set parameters" ) );
		//		Create tables
		JTabbedPane nodeGroup = new JTabbedPane();
		JTabbedPane intraEdgeGroup = new JTabbedPane();
		JTabbedPane interEdgeGroup = new JTabbedPane();
		nodeGroup.setBorder( BorderFactory.createTitledBorder( "Set column types from node table(s)" ) );
		intraEdgeGroup.setBorder( BorderFactory.createTitledBorder( "Set column types from intra-layer edge table(s)" ) );
		interEdgeGroup.setBorder( BorderFactory.createTitledBorder( "Set column types from inter-layer edge table(s)" ) );
		
		/**** Add listeners ****/
		//		Update the tables
		ListTableLayerTabs nodeTabs = new ListTableLayerTabs( frame, "node", nodeGroup, 2, ColumnType.NODE );
		ListTableLayerTabs intraEdgeTabs = new ListTableLayerTabs( frame, "intra-layer edge", intraEdgeGroup, 2, ColumnType.INTRA_EDGE );
		ListTableLayerTabs interEdgeTabs = new ListTableLayerTabs( frame, "inter-layer node", interEdgeGroup, 2, ColumnType.INTER_EDGE  );
		ListTableLayerTabs[] tabPans = { nodeTabs, intraEdgeTabs, interEdgeTabs };
		numberLayersSpinner.addChangeListener( new UpdateNumberLayers( tabPans ) );
		isNodeAlignedCheck.addChangeListener( new IsSameTableListener( frame, nodeTabs ) );
		autoDiagonalCouplingCheck.addChangeListener( new autoDiagonalCouplingListener( interEdgeTabs, interEdgeGroup ) );
		isEdgeAlignedCheck.addChangeListener( new IsSameTableListener( frame, intraEdgeTabs ) );
		isEquivalentCouplingCheck.addChangeListener( new IsSameTableListener( frame, interEdgeTabs )  );
		//		Submission
		submit.addActionListener( new Submit( frame,
				defaultNodeWeightField, defaultIntraEdgeWeightField, defaultInterEdgeWeightField,
				nodeTabs, intraEdgeTabs, interEdgeTabs
				) );
		
		/**** Set default checked boxes ****/
		isNodeAlignedCheck.setSelected(true);
		isEdgeAlignedCheck.setSelected(true);
		autoDiagonalCouplingCheck.setSelected(true);
		areInterEdgeDirectedCheck.setSelected(true);
	    
	    /**** Display ****/
		//		add main panel
		JPanel mainPanel = new JPanel( new GridBagLayout() );
		addComponentToPanel( mainPanel, paramGroup, 0, 0, 1, 1, 0, 0 );
		addComponentToPanel( mainPanel, nodeGroup, 0, 1, 1, 1, 0, 0 );
		addComponentToPanel( mainPanel, intraEdgeGroup, 0, 2, 1, 1, 0, 0 );
		addComponentToPanel( mainPanel, interEdgeGroup, 0, 3, 1, 1, 0, 0 );
		addComponentToPanel( mainPanel, submitBox, 0, 4, 1, 1, 1, 1 );
		//		add vertical scroll bar
		JScrollPane scroll = new JScrollPane( mainPanel );
		scroll.setHorizontalScrollBarPolicy( JScrollPane.HORIZONTAL_SCROLLBAR_NEVER );
		//		display
	    frame.setContentPane( scroll );
	    frame.setVisible(true);

	}
	
	/*_______________________________________
	 * 
	 *			LAYOUT FUNCTIONS
	 *_______________________________________
	 */
	
	/*
	 * Set component size
	 */
	private void setComponentSize( Component comp, int width, int height ) {
		comp.setMaximumSize( new Dimension( width, height ) );
		comp.setMinimumSize( new Dimension( width, height ) );
		comp.setPreferredSize( new Dimension( width, height ) );
	}
	
	/*
	 * Define rows of the form
	 */
	private Box defineFormRows( JLabel label, Component comp ) {
		Box box = Box.createHorizontalBox();
		box.add(label);
		box.add(comp);
		box.add(Box.createGlue());
		return box;
	}
	
	/*
	 * Add a component to a panel with GridBagConstraints
	 */
	private void addComponentToPanel( JPanel panel, Component toAdd,
			int gridX, int gridY,
			int gridW, int gridH,
			double weightX, double weightY ) {
		panel.add( toAdd, new GridBagConstraints(
				gridX, gridY, gridW, gridH, weightX, weightY,
				GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 10, 5, 10), 0, 0 )
				);
	}
	
	/*
	 * Object containing tabs setting the column types for each layer
	 */
	public class ListTableLayerTabs {
		
		private JFrame frame;
		private ArrayList<JPanel> listTabs = new ArrayList<JPanel>();
		private ArrayList<DefaultTableModel> listTableModels = new ArrayList<DefaultTableModel>();
		private ArrayList<JComboBox<String>> listSelectionCombo = new ArrayList<JComboBox<String>>();
		private String typeOfTable;
		private JTabbedPane groupTable;
		private int tableType;
		private int nbLayers;
		private boolean sharedTable = false;
		
		ListTableLayerTabs( JFrame frame,
				String typeOfTable,
				JTabbedPane groupTable,
				int numberPanels,
				int tableType ){
			this.frame = frame;
			this.typeOfTable = typeOfTable;
			this.groupTable = groupTable;
			this.nbLayers = numberPanels;
			this.tableType = tableType;
			addPanels();
		}
		
		public void setSharedTable(boolean sharedTable) {
			this.sharedTable = sharedTable;
			addPanels();
		}
		
		public void setNumberlayers(int nbLayers) {
			this.nbLayers = nbLayers;
			addPanels();
		}
		
		private void setTab(){
			JPanel tab = new JPanel();
			Box layer = Box.createVerticalBox();
			//		Table selection field
			JLabel tableSelectionLabel = new JLabel( "Select "+ typeOfTable +" table" );
			JComboBox<String> tableSelectionCombo = new JComboBox<String>();
			setComponentSize( tableSelectionCombo, 500, tableSelectionCombo.getMinimumSize().height );
			listSelectionCombo.add( tableSelectionCombo );
			//		Add CyTables
			tableSelectionCombo.addItem( "---" );
			for (String nameTable : listCyTables.keySet())
				if( ! nameTable.contains("default network") ) tableSelectionCombo.addItem( nameTable );
			//		Set element of the combobox that defines column type
			JComboBox<Object> columnTypeComboBox = new JComboBox<Object>( new ColumnTypeSelectionModel( tableType ) );
			//		Column table model
			DefaultTableModel columnTableModel = new CustomTableModel( new String[]{"Column name", "Type"}, 0 );
			listTableModels.add( columnTableModel );
			JTable columnTable = new CustomJTable( columnTableModel, columnTypeComboBox );
			columnTable.setDefaultRenderer( JComponent.class, new CustomTableCellRenderer() ); // implement our custom rendering to display the combobox
			columnTable.setPreferredScrollableViewportSize( new Dimension( 600, 100 ) );
			columnTable.getColumnModel().getColumn(0).setPreferredWidth(400);
			columnTable.getColumnModel().getColumn(1).setPreferredWidth(300);
			JScrollPane columnTablePan = new JScrollPane( columnTable );
			//		Update column table according to table selection
			tableSelectionCombo.addActionListener( new UpdateColumnTypeTable(
					frame,
					columnTableModel,
					tableSelectionCombo
					));
			//		Add selection to the tab
			Box row = defineFormRows( tableSelectionLabel, tableSelectionCombo );
			row.setToolTipText("Select a Cytoscape table that gives " + typeOfTable + "s to generate the multi-layer network.");
			layer.add( row );
			layer.add( columnTablePan ) ;
			tab.add(layer);
			listTabs.add(tab);
		}
		
		public JPanel getTab(int index) {
			return listTabs.get(index);
			
		}
		
		public void addPanels() {
			// remove previous tabs
			cleanPanels();
			// add new tabs		
			if (sharedTable) {
				setTab();
				groupTable.add( "Shared table", getTab( 0 ) );
			} else {
				if ( tableType == ColumnType.INTER_EDGE ) {
					for (int i = 1; i <= nbLayers - 1; i++) {
						setTab();
						int j = i+1;
						groupTable.add( "Layer " + i + "->" + j, getTab( i - 1 ) );
					}
				} else {
					for (int i = 1; i <= nbLayers; i++) {
						setTab();
						groupTable.add( "Layer " + i, getTab( i - 1 ) );
					}
				}
			}
		}
		
		public void cleanPanels() {
			listTabs = new ArrayList<JPanel>();
			listTableModels = new ArrayList<DefaultTableModel>();
			listSelectionCombo = new ArrayList<JComboBox<String>>();
			groupTable.removeAll();
		}
	
		public ArrayList<CyTable> getSelectedCyTable() {
			ArrayList<CyTable> tables = new ArrayList<CyTable>();
			for (JComboBox<String> comboBox : listSelectionCombo) {
				CyTable table = listCyTables.get( comboBox.getSelectedItem() ); 
				tables.add( table );
			}
			return tables;
		}
		
		public ArrayList<DefaultTableModel> getListTableModels(){ return listTableModels; }
	}
	
	/*_______________________________________
	 * 
	 *			LISTENERS
	 *_______________________________________
	 */
	
	/*
	 * When the number of layers is updated, change the possible types of columns
	 */
	private class UpdateNumberLayers implements ChangeListener{
		
		ListTableLayerTabs[] listTabs;
		
		public UpdateNumberLayers( ListTableLayerTabs[] listTabs ) {
			this.listTabs = listTabs;
		}
		
		public void stateChanged(ChangeEvent e) {
			int nbLayers = (int) numberLayers.getValue();
			for (ListTableLayerTabs tab : listTabs) {
				tab.setNumberlayers(nbLayers);
			}
		}
		
	}
	
	/*
	 * Update tabs when check box for IsSameTables is modified
	 */
	private class IsSameTableListener implements ChangeListener{
		
		ListTableLayerTabs listTabs;
		
		public IsSameTableListener( JFrame frame, ListTableLayerTabs listTabs ) {
			super();
			this.listTabs = listTabs;
		}

		public void stateChanged(ChangeEvent e) {
			if ( ((JCheckBox) e.getSource()).isSelected() ) {
				listTabs.setSharedTable(true);
			} else {
				listTabs.setSharedTable(false);
			}
		}
	}
	
	/*
	 * Listener for update tables of column types
	 */
	private class UpdateColumnTypeTable implements ActionListener{
		
		JFrame frame;
		DefaultTableModel columnTableModel;
		JComboBox<String> tableCombo;
		String previousTable = null; // to avoid to update the table if the network didn't change
		
		UpdateColumnTypeTable( JFrame frame,
				DefaultTableModel columnTableModel,
				JComboBox<String> tableCombo
				){
			super();
			this.frame = frame;
			this.columnTableModel = columnTableModel;
			this.tableCombo = tableCombo;
		}

		public void actionPerformed(ActionEvent e) {
			String currentTable = (String) tableCombo.getSelectedItem();
			if ( ! currentTable.equals("---") && ! currentTable.equals(previousTable) ) {
				// get column names
				ArrayList<CyColumn> columns = (ArrayList<CyColumn>) listCyTables.get(currentTable).getColumns();
				// reset the model
				columnTableModel.setRowCount(0);
				// update the model
				for (CyColumn cyColumn : columns) {
					if ( cyColumn.getName() != "SUID"
							&& cyColumn.getName() != "selected"
							&& cyColumn.getName() != "shared name"
							&& cyColumn.getName() != "interaction"
							&& cyColumn.getName() != "shared interaction" ) {
						columnTableModel.addRow( new Object[] {
								cyColumn.getName(),
								"-"
						});
					}
				}
				} else if( currentTable.equals("---") ) {
					columnTableModel.setRowCount(0);
				}
				// update variable
				previousTable = currentTable;
				// change height of the table
				frame.setVisible(true);
		}
	}
	
	/*
	 * 
	 */
	private class autoDiagonalCouplingListener implements ChangeListener{
		
		ListTableLayerTabs interEdgeTabs;
		JTabbedPane interEdgeGroup;

		public autoDiagonalCouplingListener(ListTableLayerTabs interEdgeTabs, JTabbedPane interEdgeGroup) {
			super();
			this.interEdgeTabs = interEdgeTabs;
			this.interEdgeGroup = interEdgeGroup;
		}

		@Override
		public void stateChanged(ChangeEvent e) {
			if ( ((JCheckBox) e.getSource()).isSelected() ) {
				isEquivalentCouplingCheck.setSelected(false);
				isEquivalentCouplingCheck.setEnabled(false);
				interEdgeGroup.setVisible(false);
				interEdgeTabs.cleanPanels();
			} else {
				isEquivalentCouplingCheck.setEnabled(true);
				interEdgeGroup.setVisible(true);
				interEdgeTabs.addPanels();
			}
		}
		
	}
	
	/*_______________________________________
	 * 
	 *			MODELS
	 *_______________________________________
	 */
	
	/*
	 * Extended model to display the combobox in the table
	 */
	private class CustomTableModel extends DefaultTableModel {
		
		private static final long serialVersionUID = -4570808925298554977L;

		public CustomTableModel( String[] columns, int rowCount ) {
			super(columns, rowCount);
		}
		
		public Class<?> getColumnClass(int colIndex) {
			return getValueAt(0, colIndex).getClass();
        }
		
		public boolean isCellEditable(int row, int column){
			if ( column == 0 ) return false; 
			else return true;
	      }
		
	}
	
	/*
	 * Extended renderer to display the combobox in the table
	 */
	private class CustomTableCellRenderer extends DefaultTableCellRenderer {
		
		private static final long serialVersionUID = 7256051599772495896L;

		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			if(value instanceof JComboBox)
				return (JComboBox<?>) value;
			else
				return this;
		  }
	}
	
	/*
	 * Extended table to display combobox
	 */
	private class CustomJTable extends JTable {

		private static final long serialVersionUID = 7259718508289082117L;
		
		JComboBox<Object> comboBox;

		public CustomJTable(DefaultTableModel model, JComboBox<Object> comboBox) {
			super(model);
			this.comboBox = comboBox;
			}
		
        //  Determine the editor to be used by row
		public TableCellEditor getCellEditor(int row, int column)
        {
            int modelColumn = convertColumnIndexToModel( column );

            if (modelColumn == 1)
            {
                return new DefaultCellEditor( comboBox );
            }
            else
                return super.getCellEditor(row, column);
        }
		
		// Make height of the table proportional to number of rows
		public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(super.getPreferredSize().width,
                getRowHeight() * getRowCount());
        }
    }
	
	/*
	 * Create model for combox box within tables
	 */
	private class ColumnTypeSelectionModel extends DefaultComboBoxModel<Object> {
		
		private static final long serialVersionUID = -2174520861073786652L;
		
		private ColumnType columnType;
		
		ColumnTypeSelectionModel( int tableType ) {
			super();
			this.columnType = new ColumnType( tableType );
		}

		public Object getElementAt(int index) { return columnType.get(index); }
		
		public int getSize() { return columnType.size(); }
		
	}
	
	/*
	 * Object containing possible column types for a table
	 */
	private class ColumnType {
		
		private ArrayList<String> columnTypeElements = new ArrayList<String>();
		private int tableType;
		private JCheckBox isCheck;
		
		static final int NODE = 1;
		static final int INTRA_EDGE = 2;
		static final int INTER_EDGE = 3;
		
		ColumnType( int tableType ){
			this.tableType = tableType;
			if ( tableType == NODE ) this.isCheck = isNodeAlignedCheck;
			else if ( tableType == INTRA_EDGE ) this.isCheck = isEdgeAlignedCheck;
			else if ( tableType == INTER_EDGE ) this.isCheck = isEquivalentCouplingCheck;
			this.rebuild();
		}
		
		public void rebuild(){
			/*
			 * WARNING: DO NOT MODIFY ELEMENTS BELOW WITHOUT UPDATING SUBMISSION
			 */
			boolean isAligned = isCheck.isSelected();
			//		Clear the list
			columnTypeElements.clear();
			//		Add basic column types
			columnTypeElements.add( "-" );
			if ( tableType == 1 ) {
				columnTypeElements.add( "Node" );
				columnTypeElements.add( "Node weight" );
			} else {
				columnTypeElements.add( "\"Interacts with\" column" );
				columnTypeElements.add( "Source node" );
				columnTypeElements.add( "Target node" );
				columnTypeElements.add( "Edge weight" );
				columnTypeElements.add( "Edge direction" );
			}
			//		Add column types if there is one tab
			int nbLayers = (int) numberLayers.getValue();
			if ( ! isAligned ) {
				columnTypeElements.add( "Other column" );
			} else {
				columnTypeElements.add( "Shared column" );
				for (int i = 1; i <= nbLayers; i++) {
					// Column types for a node table
					if ( tableType == 1 ) {
						/*columnTypeElements.add( "Node layer_" + i );*/
						columnTypeElements.add( "Node-weight layer_" + i );
						columnTypeElements.add( "Other column layer_" + i );
					// Column types for an intra-layer edge table
					} else if ( tableType == 2 ) {
						/*columnTypeElements.add( "\"Interacts with\"-column layer_" + i );
						columnTypeElements.add( "Source-node layer_" + i );
						columnTypeElements.add( "Target-node layer_" + i );*/
						columnTypeElements.add( "Edge-weight layer_" + i );
						columnTypeElements.add( "Edge-direction layer_" + i );
						columnTypeElements.add( "Other column layer_" + i );
					// Column types for an inter-layer edge table
					} else if ( i+1 <= nbLayers & tableType == 3 ) {
						int j = i + 1;
						/*columnTypeElements.add( "\"Interacts with\"-column layers_" + i + "->" + j );
						columnTypeElements.add( "Source-node layers_" + i + "->" + j );
						columnTypeElements.add( "Target-node layers_" + i + "->" + j );*/
						columnTypeElements.add( "Edge-weight layers_" + i + "->" + j );
						columnTypeElements.add( "Edge-direction layers_" + i + "->" + j );
						columnTypeElements.add( "Other column layers_" + i + "->" + j );
					}
				}
			}
		}
		
		public int size() { return columnTypeElements.size(); }
		public String get(int index) { return columnTypeElements.get(index); }
		
	}

	/*_______________________________________
	 * 
	 *			SUBMISSION
	 *_______________________________________
	 */
	
	/*
	 * Submit
	 */
	private class Submit implements ActionListener {
		
		private JFrame frame;
		private JFormattedTextField defaultNodeWeightField, defaultIntraEdgeWeightField, defaultInterEdgeWeightField;
		private ListTableLayerTabs nodeTabs, intraEdgeTabs, interEdgeTabs;

		public Submit(JFrame frame, JFormattedTextField defaultNodeWeightField,
				JFormattedTextField defaultIntraEdgeWeightField, JFormattedTextField defaultInterEdgeWeightField,
				ListTableLayerTabs nodeTabs, ListTableLayerTabs intraEdgeTabs, ListTableLayerTabs interEdgeTabs) {
			this.frame = frame;
			this.defaultNodeWeightField = defaultNodeWeightField;
			this.defaultIntraEdgeWeightField = defaultIntraEdgeWeightField;
			this.defaultInterEdgeWeightField = defaultInterEdgeWeightField;
			this.nodeTabs = nodeTabs;
			this.intraEdgeTabs = intraEdgeTabs;
			this.interEdgeTabs = interEdgeTabs;
		}

		public void actionPerformed(ActionEvent e) {
			// Run extraction
			TaskIterator converter = new TaskIterator( new MlnConverterTask(
					defaultNodeWeightField,
					defaultIntraEdgeWeightField, defaultInterEdgeWeightField,
					nodeTabs, intraEdgeTabs, interEdgeTabs,
					numberLayers,
					isNodeAlignedCheck, isEdgeAlignedCheck, isEquivalentCouplingCheck, autoDiagonalCouplingCheck,
					areIntraEdgeDirectedCheck, areInterEdgeDirectedCheck, allNodesAreQueriesCheck
					) );
			ServiceProvider.get(TaskManager.class).execute( converter, new ConverterObserver() );
		}
		
		/*
		 * Get status of the ConverterTask.
		 */
		private class ConverterObserver implements TaskObserver{
			
			public ConverterObserver() {}

			@Override
			public void taskFinished(ObservableTask task) {}

			@Override
			public void allFinished(FinishStatus finishStatus) {
				 if ( finishStatus.getType() == Type.SUCCEEDED ) {
					int another_conversion = JOptionPane.showConfirmDialog(frame,
							"The multi-layer network has been generated. Close the converter?",
							"Successful conversion", JOptionPane.YES_NO_OPTION);
					if( another_conversion == 0 ) frame.dispose();
				} else if ( finishStatus.getType() == Type.CANCELLED ) {
					JOptionPane.showConfirmDialog(frame,
							"Conversion has been aborted.",
							"Conversion aborted", JOptionPane.ERROR_MESSAGE);
				}
			}
		}
		
	}
	
	
	
}

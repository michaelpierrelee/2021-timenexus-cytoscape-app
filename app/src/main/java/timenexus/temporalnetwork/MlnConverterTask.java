package timenexus.temporalnetwork;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;

import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JOptionPane;
import javax.swing.SpinnerModel;
import javax.swing.table.DefaultTableModel;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyTable;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

import timenexus.temporalnetwork.MlnBuilder.MlnBuilderException;
import timenexus.temporalnetwork.MlnReader.MlnReaderException;
import timenexus.temporalnetwork.TimeNexusConverterPanel.ListTableLayerTabs;
import timenexus.utils.MlnException;

/*
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class MlnConverterTask extends AbstractTask {
	
	private JFormattedTextField defaultNodeWeightField, defaultIntraEdgeWeightField, defaultInterEdgeWeightField;
	private ListTableLayerTabs nodeTabs, intraEdgeTabs, interEdgeTabs;
	private String[] typeOfTable = {"node", "intra-layer edges", "inter-layer edges"};
	private SpinnerModel numberLayers;
	private JCheckBox isNodeAlignedCheck, isEdgeAlignedCheck, isEquivalentCouplingCheck, autoDiagonalCouplingCheck,
	areIntraEdgeDirectedCheck, areInterEdgeDirectedCheck, allNodesAreQueriesCheck;
	// Enable to cancel the task
	volatile boolean cancelled = false;
	
	public MlnConverterTask(JFormattedTextField defaultNodeWeightField,
			JFormattedTextField defaultIntraEdgeWeightField, JFormattedTextField defaultInterEdgeWeightField,
			ListTableLayerTabs nodeTabs, ListTableLayerTabs intraEdgeTabs, ListTableLayerTabs interEdgeTabs,
			SpinnerModel numberLayers,
			JCheckBox isNodeAlignedCheck, JCheckBox isEdgeAlignedCheck, JCheckBox isEquivalentCouplingCheck,
			JCheckBox autoDiagonalCouplingCheck, JCheckBox areIntraEdgeDirectedCheck,
			JCheckBox areInterEdgeDirectedCheck, JCheckBox allNodesAreQueriesCheck
			) {
		this.defaultNodeWeightField = defaultNodeWeightField;
		this.defaultIntraEdgeWeightField = defaultIntraEdgeWeightField;
		this.defaultInterEdgeWeightField = defaultInterEdgeWeightField;
		this.nodeTabs = nodeTabs;
		this.intraEdgeTabs = intraEdgeTabs;
		this.interEdgeTabs = interEdgeTabs;
		this.numberLayers = numberLayers;
		this.isNodeAlignedCheck = isNodeAlignedCheck;
		this.isEdgeAlignedCheck = isEdgeAlignedCheck;
		this.isEquivalentCouplingCheck = isEquivalentCouplingCheck;
		this.autoDiagonalCouplingCheck = autoDiagonalCouplingCheck;
		this.areIntraEdgeDirectedCheck = areIntraEdgeDirectedCheck;
		this.areInterEdgeDirectedCheck = areInterEdgeDirectedCheck;
		this.allNodesAreQueriesCheck = allNodesAreQueriesCheck;
	}
	
	/*
	 * Cancel the task.
	 */
	@Override
	public void cancel() { cancelled = true; }

	@Override
	public void run(TaskMonitor taskMonitor) throws MlnConverterException {
		try {
			taskMonitor.setTitle("TimeNexus Converter");
			//		Get parameters
			int nbLayers = (int) numberLayers.getValue();
			double defaultNodeWeight = (double) defaultNodeWeightField.getValue();
			double defaultIntraEdgeWeight = (double) defaultIntraEdgeWeightField.getValue();
			double defaultInterEdgeWeight = (double) defaultInterEdgeWeightField.getValue();
			boolean isNodeAligned = isNodeAlignedCheck.isSelected();
			boolean isEdgeAligned = isEdgeAlignedCheck.isSelected();
			boolean isEquivalentCoupling = isEquivalentCouplingCheck.isSelected();
			boolean areIntraEdgeDirected = areIntraEdgeDirectedCheck.isSelected();
			boolean areInterEdgeDirected = areInterEdgeDirectedCheck.isSelected();
			boolean allNodesAreQueries = allNodesAreQueriesCheck.isSelected();
			//		Get column-type tables
			ArrayList<DefaultTableModel> nodeColTableModels = nodeTabs.getListTableModels();
			ArrayList<DefaultTableModel> intraEdgeColTableModels = intraEdgeTabs.getListTableModels();
			ArrayList<DefaultTableModel> interEdgeColTableModels = interEdgeTabs.getListTableModels();
			//		Get column types from each column-type table
			ArrayList<Hashtable<String, String>> nodeColTypes = new ArrayList<Hashtable<String, String>>();
			ArrayList<Hashtable<String, String>> intraEdgeColTypes = new ArrayList<Hashtable<String, String>>();
			ArrayList<Hashtable<String, String>> interEdgeColTypes = new ArrayList<Hashtable<String, String>>();
			nodeColTypes = getColumnTypes( nodeColTableModels, "nodes", isNodeAligned, nbLayers );
			intraEdgeColTypes = getColumnTypes( intraEdgeColTableModels, "intra-layer edges", isEdgeAligned, nbLayers );
			if ( ! autoDiagonalCouplingCheck.isSelected() ) // in this case, the inter-layer edges are not defined by the user
				interEdgeColTypes = getColumnTypes( interEdgeColTableModels, "inter-layer edges", isEquivalentCoupling, nbLayers - 1 );
			
			//		Convert data to multi-layer network format
			taskMonitor.setStatusMessage("Getting the input data...");
			MlnBuilder mlnNetwork = new MlnBuilder( nbLayers );
			// Add nodes
			convertTable( mlnNetwork, MlnBuilder.NODE, nbLayers,
					nodeTabs, nodeColTypes, defaultNodeWeight, false );
			// Set all nodes as queries if asked by the user
			if ( allNodesAreQueries ) setAllNodesAsQueries( mlnNetwork );
			// Add intra-layer edges
			convertTable( mlnNetwork, MlnBuilder.INTRA_EDGE, nbLayers,
					intraEdgeTabs, intraEdgeColTypes, defaultIntraEdgeWeight, areIntraEdgeDirected );
			// Add inter-layer edges
			if ( !autoDiagonalCouplingCheck.isSelected() )
				convertTable( mlnNetwork, MlnBuilder.INTER_EDGE, nbLayers - 1,
						interEdgeTabs, interEdgeColTypes, defaultInterEdgeWeight, areInterEdgeDirected );
			else // If inter-layer edges are not defined by the user
				createInterEdgeTable( mlnNetwork, nbLayers - 1,
						interEdgeTabs, interEdgeColTypes, defaultInterEdgeWeight, areInterEdgeDirected );
	
			//		Check if the new multilayer network is consistent
			taskMonitor.setStatusMessage("Checking consistency of the input data...");
			// Check for each layer whether nodes from intra-edge tables are within node-layer tables
			for (int i = 0; i < mlnNetwork.getNumberLayers(); i++) {
				// Get node-layers
				HashSet<Object> nodeLayers = new HashSet<Object>( mlnNetwork.getNodeLayers().get(i) );
				// Combine sources and targets
				HashSet<Object> intraEdgeNodes = new HashSet<Object>( mlnNetwork.getIntraSourceLayers().get(i) );
				intraEdgeNodes.addAll( mlnNetwork.getIntraTargetLayers().get(i) );
				// Remove sources and targets which are not in the node-layer list
				int nodeNumber = intraEdgeNodes.size();
				intraEdgeNodes.retainAll(nodeLayers);
				// The number of sources and targets should not have changed
				if( intraEdgeNodes.size() != nodeNumber ) {
					int j = i+1;
					throw new MlnConverterException(
							"Some nodes from intra-layer edges are not within the node table for the layer "+j+".",
							"Conversion error: inconsistent tables ", JOptionPane.ERROR_MESSAGE);
				}
				
			}
			// Same for inter-layer edges
			for (int i = 0; i < mlnNetwork.getNumberLayers() - 1; i++) {
				// Get node-layers
				HashSet<Object> nodeLayers1 = new HashSet<Object>( mlnNetwork.getNodeLayers().get(i) );
				HashSet<Object> nodeLayers2 = new HashSet<Object>( mlnNetwork.getNodeLayers().get(i+1) );
				// Get sources and targets
				HashSet<Object> interEdgeNodeLayers1 = new HashSet<Object>( mlnNetwork.getInterSourceLayers().get(i) );
				HashSet<Object> interEdgeNodeLayers2 = new HashSet<Object>( mlnNetwork.getInterTargetLayers().get(i) );
				// Remove sources and targets which are not in the node-layer lists
				int nodeNumber1 = interEdgeNodeLayers1.size(), nodeNumber2 = interEdgeNodeLayers2.size();
				interEdgeNodeLayers1.retainAll(nodeLayers1);
				interEdgeNodeLayers2.retainAll(nodeLayers2);
				// The number of sources and targets should not have changed
				int j = i+1;
				int k = j+1;
				if( interEdgeNodeLayers1.size() != nodeNumber1 ) {
					throw new MlnConverterException(
							"Some sources from "+ j +"->"+ k +" inter-layer edges are not within "
									+ "the node table of the layer "+j+".",
							"Conversion error: inconsistent tables ", JOptionPane.ERROR_MESSAGE);
				} else if( interEdgeNodeLayers2.size() != nodeNumber2 ) {
					throw new MlnConverterException(
							"Some targets from "+ j +"->"+ k +" inter-layer edges are not within "
									+ "the node table of the layer "+k+".",
							"Conversion error: inconsistent tables ", JOptionPane.ERROR_MESSAGE);
				}
			}
	
			//		Create the multi-layer network
			taskMonitor.setStatusMessage("Building the multi-layer network...");
			MlnWriter.createMultiLayerNetwork( mlnNetwork, "Multi-layer network" );
			
		} catch (MlnException err) {
			throw new MlnConverterException( err.getMessageTitle() + "\n\n" + err.getMessage(),
					err.getMessageTitle(), err.getMessageType(), err);
		} catch (Exception err) {
			throw new MlnConverterException( err.getMessage(),
					"Conversion error:" + err.getClass().getName() + "\n\n" + err.getMessage(),
					JOptionPane.ERROR_MESSAGE, err);
		}
	}
	

	/*
	 * All node-layers are set as node-queries.
	 */
	private void setAllNodesAsQueries( MlnBuilder mlnNetwork ) throws MlnConverterException {
		try {
			for (int i = 0, k = 1; i < mlnNetwork.getNumberLayers(); i++, k++)
				mlnNetwork.addOtherColum (MlnBuilder.NODE, i, "Query_"+k, Boolean.class,
						Collections.nCopies(mlnNetwork.getNodeLayerCount(i), true) );
		} catch (MlnBuilderException err) {
			throw new MlnConverterException(err.getMessage(), err.getMessageTitle(), err.getMessageType(), err);
		}
	}
	
	/*
	 * @return column type for each column name
	 */
	private ArrayList<Hashtable<String, String>> getColumnTypes( ArrayList<DefaultTableModel> colTableModels, String typeOfTable,
			boolean isAligned, int nbLayers) throws MlnConverterException{
		ArrayList<Hashtable<String, String>> data = new ArrayList<Hashtable<String, String>>();
		//		Go through each tab
		int tab = 0;
		for (DefaultTableModel model : colTableModels) {
			Hashtable<String, String> columnTypes = new Hashtable<String, String>();
			tab = tab + 1;
			// Get data
			for (int i = 0; i < model.getRowCount(); i++) {
				String colName = (String) model.getValueAt(i, 0);
				String colType = (String) model.getValueAt(i, 1);
				if (colType != "-") {
					// Change the name of the column type "Shared column" and "Other column"
					// to enable multiple keys of them within the hashtable
					if ( colType.equals("Shared column") || colType.contains("Other column") )
						columnTypes.put( i + colType, colName );
					// Test that all columns are present only one (except "shared column") 
					else if ( ! columnTypes.containsKey(colType) )
						columnTypes.put( colType, colName );
					else
						throw new MlnConverterException(
								"The type \"" + colType + "\" for the column \"" + colName
									+ "\" is duplicated.\n\nError within the tab " + tab
									+ " for \"" + typeOfTable + "\".",
								"Conversion error: duplicated column type", JOptionPane.ERROR_MESSAGE);
				}
			}
			
			//		Test incompatible and missing types
			ArrayList<String> missingTypes = new ArrayList<String>();
			int incompatibility = 0;
			// Nodes
			if ( typeOfTable.equals("nodes")  ) {
				// test of the defined types
				boolean containsNode = false;
				int containsNodeLayer = 0;
				for (String colType : columnTypes.keySet()) {
					if ( colType.equals("Node") ) containsNode = true;
					else if( colType.contains("layer") ) containsNodeLayer++;
				}
				// missing node columns
				if ( ! containsNode && containsNodeLayer == 0 ) missingTypes.add("node");
				else if ( containsNode && containsNodeLayer > 0 ) incompatibility = 2;
				else if ( ! containsNode && containsNodeLayer > 0 && containsNodeLayer < nbLayers ) incompatibility = 3;
				// incompatible columns
				if ( isAligned )
					incompatibility = testIncompatibleTypes(columnTypes, "Node weight", "Node-weight", nbLayers);
			// Edges
			} else if ( ! typeOfTable.equals("nodes") ) {
				// test of the the defined types
				boolean containS = false, containT = false, containI = false;
				int containSL = 0, containTL = 0, containIL = 0;
				for (String colType : columnTypes.keySet()) {
					if (colType.contains("Source") && colType.contains("layer")) containSL++;
					else if (colType.contains("Target") && colType.contains("layer")) containTL++;
					else if (colType.contains("Interact") && colType.contains("layer")) containIL++;
					else if (colType.contains("Source") && ! colType.contains("layer")) containS = true;
					else if (colType.contains("Target") && ! colType.contains("layer")) containT = true;
					else if (colType.contains("Interact") && ! colType.contains("layer")) containI = true;
				}
				// search missing and incompatible types
				if ( ! isAligned ) {
					if ( ! containS && ! containI ) missingTypes.add("Source node (or \"interacts with\")");
					if ( ! containT && ! containI ) missingTypes.add("Target node (or \"interacts with\")");
					if ( ( containS || containT ) && containI ) incompatibility = 1;
				} else {
					// missing types
					if ( ! containS && ! containI && containSL == 0 && containIL == 0 ) missingTypes.add("Source node (or \"interacts with\")");
					if ( ! containT && ! containI && containTL == 0 && containIL == 0 ) missingTypes.add("Target node (or \"interacts with\")");
					// incompatibilities for edge types
					if ( ( ( containS || containT ) && containI ) ||
							( ( containSL > 0 || containTL > 0 ) && containIL > 0 && containIL+(containSL+containTL)/2 != nbLayers ) )
						incompatibility = 1;
					else if ( ( containS || containT || containI ) && ( containSL > 0 || containTL > 0 || containIL > 0 ) )
						incompatibility = 2;
					else if ( ! ( containS || containT || containI )
							&& ( containIL+(containSL+containTL)/2 >0 && containIL+(containSL+containTL)/2 < nbLayers ) )
						incompatibility = 3;
					// incompatibilities for other types
					int a = testIncompatibleTypes(columnTypes, "Edge weight", "Edge-weight", nbLayers),
						b = testIncompatibleTypes(columnTypes, "Edge direction", "Edge-direction", nbLayers);
					if ( a > 0) incompatibility = a;
					if ( b > 0) incompatibility = b;
					
				}
			}
			
			//		Return errors
			if ( ! missingTypes.isEmpty() )
				throw new MlnConverterException(
						"The following types have to be defined: \"" + missingTypes.toString()
							+ "\".\n\nError within the tab " + tab
							+ " for \"" + typeOfTable + "\".",
						"Conversion error: missing column type", JOptionPane.ERROR_MESSAGE);
			else if ( incompatibility == 1 )
				throw new MlnConverterException("The types \"source node\" and \"target node\" are incompatible with \"interact with\".\n\n"
							+ "Error within the tab " + tab
							+ " for \"" + typeOfTable + "\".",
						"Conversion error: incompatibility with interact type", JOptionPane.ERROR_MESSAGE);
			else if ( incompatibility == 2 )
				throw new MlnConverterException(
						"The types \"source/target node\" and \"interact with\" are incompatible with \"source/target-node layer i\" and \"interact-with layer i\",\n"
								+ "as well as \"node\" with \"node layer i\" and \"node weight\" with \"node-weight layer i\".\n\n"
								+ "Error within the tab " + tab
								+ " for \"" + typeOfTable + "\".",
						"Conversion error: incompatibility between unique and shared types", JOptionPane.ERROR_MESSAGE);
			else if ( incompatibility == 3 )
				throw new MlnConverterException(
						"If the types \"node layer i\", \"node-weight layer i\", or \"source/target layer i(->j)\" and \"interact with layer i(->j)\", "
							+ "are used, then all layers have to be defined.\n\n"
							+ "Error within the tab " + tab
							+ " for \"" + typeOfTable + "\".",
						"Conversion error: all layers are not defined", JOptionPane.ERROR_MESSAGE);
			// Add to the array
			data.add(columnTypes);
		}
		
		return data;
	}
	
	private int testIncompatibleTypes( Hashtable<String, String> columnTypes,
			String typeX, String typeXL, int nbLayers ) {
		// test defined types
		boolean containX = false;
		int containXL = 0;
		for (String type : columnTypes.keySet()) {
			if (type.contains( typeXL ) && type.contains("layer")) containXL = containXL + 1;
			else if (type.contains( typeX )) containX = true;
		}
		// search incompatible types
		if ( containX && containXL > 0 ) return 2; // incompatible X and XL types
		//else if ( ! containX && containXL < nbLayers && containXL > 0 ) return 3; // not all layer are defined
		else return 0;
	}
	
	private void convertTable( MlnBuilder mlnNetwork, int tableType, int nbLayers,
			ListTableLayerTabs listTableLayerTabs, ArrayList<Hashtable<String, String>> columnTypes,
			double defaultWeight, boolean areEdgeDirected ) throws MlnConverterException {
		//		Get columns from each selected tables
		ArrayList<CyTable> selectedCyTables = listTableLayerTabs.getSelectedCyTable();
		int nbTabs = selectedCyTables.size();
		//		Go through each tab
		for (int tab = 0; tab < nbTabs; tab++) {
			//		Add known column types
			CyTable cyTable = selectedCyTables.get(tab);
			ArrayList<String> colTypes = new ArrayList<String>( columnTypes.get(tab).keySet() );
			ArrayList<String> colNames = new ArrayList<String>( columnTypes.get(tab).values() );
			Boolean weight = false, direction = false;
			//		Go through each column with a selected type
			for (int j = 0; j < colNames.size(); j++) {
				String type = colTypes.get(j);
				String name = colNames.get(j);
				CyColumn col = cyTable.getColumn( name );
				// Check if the cyColumn has a type compatible with the column type selected by the user
				if ( checkColType( type, cyTable.getColumn( name ).getType() ) ) {
					// Parse interact column
					Collection<String> sources = null,  targets = null;
					if ( type.contains("Interact") ) {
						try {
							ArrayList<Collection<String>> data = MlnReader.parseInteractColumn( col );
							sources = data.get(0);
							targets = data.get(1);
						} catch (MlnReaderException e) {
							int t = tab+1;
							throw new MlnConverterException(
									"The \"interacts with\" column cannot be parsed."
											+" It should fit the format: \"<source> (interacts with) <target>\"."
											+ "\n\nError within the tab " + t + " for the column \"" + name
											+ "\" for \"" + typeOfTable[tableType] + "\".",
									"Conversion error: incompatible types within a selected table", JOptionPane.ERROR_MESSAGE, e);
						}
					}
					// Add column for each layer (1 panel for each layer)
					if ( nbTabs > 1 ) {
						if ( type.contains("Interact") ) addColumn(mlnNetwork, tab, sources, targets, type, tableType);
						else addColumn(mlnNetwork, tab, col, name, type, tableType, defaultWeight, areEdgeDirected);
					// Copy the column in the right layer (1 panel to define all layers)
					} else if ( type.contains("layer") ) {
						int layer;
						// Get number of the layer
						if ( tableType != MlnBuilder.INTER_EDGE )
							layer = Integer.parseInt( type.split("_")[1] ) - 1; // nodes and intra-edges
						else
							layer = Integer.parseInt( type.split("->")[1] ) - 2; // nodes and intra-edges
						// Add column for "interact" layer-column or other layer-columns
						if ( type.contains("Interact") )
							addColumn(mlnNetwork, layer, sources, targets, type, tableType);
						else
							addColumn(mlnNetwork, layer, col, name, type, tableType, defaultWeight, areEdgeDirected);
					}
					// Copy the column for each layer (1 panel to define all layers)
					else {
						for (int k = 0; k < nbLayers; k++) {
							if ( type.contains("Interact") ) addColumn(mlnNetwork, k, sources, targets, type, tableType);
							else addColumn(mlnNetwork, k, col, name, type, tableType, defaultWeight, areEdgeDirected);
						}
					}
				} else
					throw new MlnConverterException(
							"The weight column should be of type 'double' (float number)"
									+ " and direction column of type 'boolean' (true/false)."
									+ "\n\nError within the tab " + j + "\" for the column \"" + name
									+ "\" for \"" + typeOfTable[tableType] + "\".",
							"Conversion error: incompatible types within a selected table", JOptionPane.ERROR_MESSAGE);
				// Check if columns related to a default parameter are not defined
				if ( type.contains("weight") ) weight = true;
				if ( type.contains("direction") ) direction = true;
			}
			//		Create columns according to parameters 
			for (int j = 0; j < colNames.size(); j++) {
				try {
					// Weight column
					if( ! weight ) {
						Collection<Double> rows = new ArrayList<Double>();
						for (int k = 0; k < cyTable.getRowCount(); k++) rows.add( defaultWeight );
						// 1 panel for each layer
						if ( nbTabs > 1 ) mlnNetwork.addWeight(tableType, tab, rows);
						// 1 panel to define all layers
						else
							for (int layer = 0; layer < nbLayers; layer++) mlnNetwork.addWeight(tableType, layer, rows);
					}
					// Direction column
					if ( ! direction && tableType != MlnBuilder.NODE ) {
						Collection<Boolean> rows = new ArrayList<Boolean>();
						for (int k = 0; k < cyTable.getRowCount(); k++) rows.add( areEdgeDirected );
						// 1 panel for each layer
						if ( nbTabs > 1 ) mlnNetwork.addDirection(tableType, tab, rows);
						// 1 panel to define all layers
						else
							for (int layer = 0; layer < nbLayers; layer++) mlnNetwork.addDirection(tableType, layer, rows);
					}	
				} catch(MlnBuilderException e) {
					throw new MlnConverterException( e.getMessage(), e.getMessageTitle(), e.getMessageType(), e );
				}
			}
		}
	}
	
	/*
	 * Create inter-layer edges such as the layers have a diagonal coupling.
	 * An inter-layer edge is created for a node if this node is present in the two layers.
	 * If a node is in the layer N but not in the layer N+1, 
	 */
	private void createInterEdgeTable(MlnBuilder mlnNetwork, int nbLayers, ListTableLayerTabs interEdgeTabs,
			ArrayList<Hashtable<String, String>> interEdgeColTypes, double defaultInterEdgeWeight, boolean areInterEdgeDirected
			) throws MlnConverterException {
			// Get list list of nodes for each layer
			ArrayList<ArrayList<String>> nodeLayers = mlnNetwork.getNodeLayers();
			// Create inter-layer edges
			for (int coupling = 0; coupling < nbLayers; coupling++) {
				// Search nodes which are shared by pairwise layers
				ArrayList<String> intersection = new ArrayList<String>( nodeLayers.get(coupling) );
				ArrayList<String> nodesLayer2 = nodeLayers.get(coupling+1);
				intersection.retainAll(nodesLayer2);
				// Create columns of directions and weights
				Collection<Boolean> directions = new ArrayList<Boolean>();
				Collection<Double> weights = new ArrayList<Double>();
				for (int i = 0; i < intersection.size(); i++) {
					directions.add( areInterEdgeDirected );
					weights.add( defaultInterEdgeWeight );
				}
				// Add columns
				try {
					mlnNetwork.addSourceColumn( MlnBuilder.INTER_EDGE, coupling, intersection );
					mlnNetwork.addTargetColumn( MlnBuilder.INTER_EDGE, coupling, intersection );
					mlnNetwork.addDirection( MlnBuilder.INTER_EDGE, coupling, directions );
					mlnNetwork.addWeight( MlnBuilder.INTER_EDGE, coupling, weights );
				} catch(MlnBuilderException e) {
					throw new MlnConverterException( e.getMessage(), e.getMessageTitle(), e.getMessageType(), e );
				}
			}
	}
	
	/*
	 * Add a column into the MLN from a CyTable
	 */
	private void addColumn( MlnBuilder mlnNetwork, int layer, CyColumn col, String colName, String colType, int tableType,
			Double defaultWeight, Boolean defaultDirection )
			throws MlnConverterException {
		try {
			if ( colType.equals("Node") )
				mlnNetwork.addNodeColumn( tableType, layer, convertNodeNames( col.getValues( col.getType() ) ) );
			else if ( colType.contains("Source") )
				mlnNetwork.addSourceColumn( tableType, layer, convertNodeNames( col.getValues( col.getType() ) ) );
			else if ( colType.contains("Target") )
				mlnNetwork.addTargetColumn( tableType, layer, convertNodeNames( col.getValues( col.getType() ) ) );
			else if ( colType.contains("weight") ) {
				Collection<Double> weights = fillNullWeightColumn( col.getValues( Double.class ), defaultWeight );
				mlnNetwork.addWeight( tableType, layer, weights );
			} else if ( colType.contains("direction") ){
				Collection<Boolean> directions = fillNullDirectionColumn( col.getValues( Boolean.class ), defaultDirection );
				mlnNetwork.addDirection( tableType, layer, directions );
			} else if ( colType.contains("Shared column") || colType.contains("Other column") )
				mlnNetwork.addOtherColum( tableType, layer, colName, col.getType(), col.getValues( col.getType() ) );
		} catch(MlnBuilderException e) {
			throw new MlnConverterException( e.getMessage(), e.getMessageTitle(), e.getMessageType(), e );
		}
	}
	
	/*
	 * Add source and target columns into the MLN from interaction columns of CyTables
	 */
	private void addColumn( MlnBuilder mlnNetwork, int layer, Collection<String> sources, Collection<String> targets,
			String colType, int tableType ) throws MlnConverterException {
		if ( colType.contains("Interact") ) {
			try {
				mlnNetwork.addSourceColumn(tableType, layer, sources);
				mlnNetwork.addTargetColumn(tableType, layer, targets);
			} catch(MlnBuilderException e) {
				throw new MlnConverterException( e.getMessage(), e.getMessageTitle(), e.getMessageType(), e );
			}
		}
	}
	
	/*
	 * Convert node names into strings.
	 */
	private Collection<String> convertNodeNames( Collection<Object> nodeNames ){
		List<String> strings = new ArrayList<>(nodeNames.size());
		for (Object object : nodeNames) {
		    strings.add(Objects.toString(object, null));
		}
		return strings;
	}
	
	/*
	 * Complete null elements of weight columns
	 */
	private Collection<Double> fillNullWeightColumn( List<Double> rows, Double defaultWeight ) {
		Collection<Double> filledRows = new ArrayList<Double>();
		for (int i = 0; i < rows.size(); i++) {
			if ( rows.get(i) == null ) filledRows.add(defaultWeight);
			else filledRows.add(rows.get(i));
		}
		return filledRows;
	}
	
	/*
	 * Complete null elements of direction columns
	 */
	private Collection<Boolean> fillNullDirectionColumn( List<Boolean> rows, Boolean defaultDirection ) {
		Collection<Boolean> filledRows = new ArrayList<Boolean>();
		for (int i = 0; i < rows.size(); i++) {
			if ( rows.get(i) == null ) filledRows.add(defaultDirection);
			else filledRows.add(rows.get(i));
		}
		return filledRows;
	}
	
	private boolean checkColType( String colType, Class<?> classType ) {
		if ( colType.contains("weight") && ! Number.class.isAssignableFrom(classType) )
			return false;
		else if ( colType.contains("direction") && ! Boolean.class.isAssignableFrom(classType) )
			return false;
		return true;
	}
	

	/*_______________________________________
	 * 
	 *			EXCEPTION
	 *_______________________________________
	 */
	
	public static class MlnConverterException extends MlnException {

		private static final long serialVersionUID = 1L;
		
		public MlnConverterException(String message, String messageTitle, int messageType) {
			super(message, messageTitle, messageType);
		}
		
		public MlnConverterException(String message, String messageTitle, int messageType, Throwable cause) {
			super(message, messageTitle, messageType, cause);
		}
		
	}
}

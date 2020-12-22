package timenexus.temporalnetwork;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNetworkTableManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyTableFactory;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.session.CyNetworkNaming;

import timenexus.temporalnetwork.MlnBuilder.EdgeLayer;
import timenexus.temporalnetwork.MlnBuilder.MlnColumn;
import timenexus.temporalnetwork.MlnBuilder.NodeLayer;
import timenexus.utils.MlnException;
import timenexus.utils.ServiceProvider;

/*
 * This singleton class can transform an object from MlnBuilder.java into a multi-layer
 * network represented as a collection of networks and tables within Cytoscape.
 * 
 * A subnetwork is a layer if, within the network table, the column "multi-layer network" is
 * set to "true" and if the column "layer ID" is the rank of the layer, e.g. an ID of "1"
 * means that the layer is the first and should be followed by the layer with an ID of "2".
 * In this way, the inter-layer edge table connecting the layer 1 to the layer 2 must have
 * the name "1->2", even if the inter-layer edges are not directed.
 * 
 * The following column names should be respected:
 * - For node table: "name" (node ID) "Weight" (node's weight).
 * - For intra-layer edge table: "Source" (source-node ID of the edge),
 * "Target" (target-node), "Weight" (edge's weight), "Direction" (edge's direction).
 * - For inter-layer edge table: same as for intra-layer edge.
 * The task reads the column "name", however it must be identical to the column "shared name".
 * 
 * Edge directions of the flattened network depend on the direction given by the input.
 * Aggregated network are undirected.
 * 
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public final class MlnWriter {
	
	public final static String IS_MLN = "Multi-layer network";
	public final static String LAYER_ID = "Layer ID";
	public final static String FLAT_NETWORK = "Flattened network";
	public final static String AGG_NETWORK = "Aggregated network";
	public final static String EDGE_LABEL = "Edge label";
	
	private static CyTableFactory serviceTableFactory = ServiceProvider.get(CyTableFactory.class);
	private static CyNetworkTableManager serviceNetTableManager = ServiceProvider.get(CyNetworkTableManager.class);
	private static CyNetworkManager serviceNetworkManager = ServiceProvider.get(CyNetworkManager.class);
	private static CyNetworkNaming serviceNetworkNaming = ServiceProvider.get(CyNetworkNaming.class);
	private static CyNetworkFactory serviceNetworkFactory = ServiceProvider.get(CyNetworkFactory.class); 

	private MlnWriter(){ throw new RuntimeException(); }
	
	/*
	 * Check uniqueness of node-layers within each layer.
	 */
	public static void checkNodeLayerUniqueness( MlnBuilder mlnData ) throws MlnWriterException {
		ArrayList<ArrayList<String>> nodeLayers = mlnData.getNodeLayers();
		for (int i = 0; i < nodeLayers.size(); i++) {
			ArrayList<String> nodes = nodeLayers.get(i);
			HashSet<String> uniqueNodes = new HashSet<String>(nodes);
			int j =  i + 1;
			if ( uniqueNodes.size() != nodes.size() ) throw new MlnWriterException("Node names are not unique within the layer " + j,
					"Duplicated node names", JOptionPane.ERROR_MESSAGE );
		}
	}
	
	/*
	 * Create a multi-layer network within Cytoscape.
	 */
	public static void createMultiLayerNetwork( MlnBuilder mlnData, String mlnName ) throws MlnWriterException {
		checkNodeLayerUniqueness( mlnData ); // check
		
		CyNetwork cyMlnNetwork = serviceNetworkFactory.createNetwork();
		CyRootNetwork rootNetwork = ( (CySubNetwork) cyMlnNetwork ).getRootNetwork(); 
		int nbLayers = mlnData.getNumberLayers();
		
		//		Set name of the network collection
		String rootName = serviceNetworkNaming.getSuggestedNetworkTitle( mlnName );
		rootNetwork.getDefaultNetworkTable().getRow ( rootNetwork.getSUID() ).set( "name", rootName );
		
		//		Add column to identify network as multi-layer network
		CyTable rootNetworkTable = rootNetwork.getSharedNetworkTable();
		rootNetworkTable.createColumn( IS_MLN, Boolean.class, false );
		
		//		Create flattened network
		CySubNetwork flattenedNet = createFlattenedNetwork( mlnData, rootNetwork, nbLayers );
		serviceNetworkManager.addNetwork(flattenedNet);
		
		//		Create aggregated network and layer-networks
		List<Integer> layers = new ArrayList<Integer>();
		for (int i = 0; i < nbLayers; i++) layers.add( i + 1 );
		createMLNFromFlatNetwork( flattenedNet, layers );
		
		
	}
	
	/*
	 * Create a flattened network from the MlnBuilder object.
	 */
	public static CySubNetwork createFlattenedNetwork( MlnBuilder mlnData, CyRootNetwork rootNetwork, int nbLayers ) throws MlnWriterException {
		//		Add flattened network
		CySubNetwork flattenedNet = rootNetwork.addSubNetwork();
		CyTable flatNetworkTable = flattenedNet.getTable(CyNetwork.class, CyNetwork.LOCAL_ATTRS);
		CyTable sharedNodeTable = flattenedNet.getTable(CyNode.class, CyNetwork.DEFAULT_ATTRS);
		CyTable sharedEdgeTable = flattenedNet.getTable(CyEdge.class, CyNetwork.DEFAULT_ATTRS);
		CyTable localNodeTable = flattenedNet.getTable(CyNode.class, CyNetwork.LOCAL_ATTRS);
		CyTable localEdgeTable = flattenedNet.getTable(CyEdge.class, CyNetwork.LOCAL_ATTRS);
		// Give name to the network
		String netName = serviceNetworkNaming.getSuggestedNetworkTitle( FLAT_NETWORK );
		flatNetworkTable.getRow( flattenedNet.getSUID() ).set( CyNetwork.NAME, netName );
		flatNetworkTable.getRow( flattenedNet.getSUID() ).set( IS_MLN, true );
		// Add column to identify origin of rows		
		flatNetworkTable.createColumn( FLAT_NETWORK, Boolean.class, false ); 
		flatNetworkTable.getRow( flattenedNet.getSUID() ).set( FLAT_NETWORK, true );
		localNodeTable.createColumn( MlnWriter.LAYER_ID, Integer.class, false );
		localEdgeTable.createColumn( MlnWriter.LAYER_ID, Integer.class, false );
		localEdgeTable.createColumn( "Edge label", String.class, false );
		// Add node-table specific column
		sharedNodeTable.createColumn( MlnBuilder.WEIGHT, Double.class, false );
		// Add edge-table specific column
		sharedEdgeTable.createColumn( MlnBuilder.WEIGHT, Double.class, false );
		sharedEdgeTable.createColumn( MlnBuilder.DIRECTION, Boolean.class, false );
		// Add other columns
		for (int layerID = 0; layerID < nbLayers; layerID++) {
			// Node
			NodeLayer nodeLayer = mlnData.getNodeLayerTables(layerID);
			for ( MlnColumn<?> col : nodeLayer.getOtherColumns() ) addColFromMlnToCy( sharedNodeTable, col );
			// Intra-layer edge
			EdgeLayer intraEdgeLayer = mlnData.getIntraEdgeLayerTables(layerID);
			for ( MlnColumn<?> col : intraEdgeLayer.getOtherColumns() ) addColFromMlnToCy( sharedEdgeTable, col );
			// Inter-layer edge
			if ( layerID < nbLayers - 1 ) {
				EdgeLayer interEdgeLayer = mlnData.getIntraEdgeLayerTables(layerID);
				for ( MlnColumn<?> col : interEdgeLayer.getOtherColumns() ) addColFromMlnToCy( sharedEdgeTable, col );
			}
		}
		
		//		Fill node and intra-layer edges of the flattened network
		for (int i = 0; i < nbLayers; i++) {
			int layerID = i + 1;
			Hashtable<String, CyNode> addedNodes = new Hashtable<String, CyNode>();
			// Node table
			NodeLayer nodeLayer = mlnData.getNodeLayerTables(i);
			for (int j = 0; j < nodeLayer.getNodes().size(); j++) {
				// Create row
				CyNode node = flattenedNet.addNode();
				CyRow nodeRow = flattenedNet.getRow( node );
				addedNodes.put( nodeLayer.getNode(j), node );
				// Add layer ID to node name
				String nodeNameWithID =  nodeLayer.getNode(j) + "_" + layerID;
				// Fill columns of the row
				nodeRow.set( CyNetwork.NAME, nodeNameWithID );
				nodeRow.set( MlnBuilder.WEIGHT, nodeLayer.getWeight(j) );
				for ( MlnColumn<?> col : nodeLayer.getOtherColumns() ) nodeRow.set( col.getName(), col.get(j) );
				// Identify origin of the node-layer
				nodeRow.set( LAYER_ID, layerID );
			}
			// Edge table
			EdgeLayer intraEdgeLayer = mlnData.getIntraEdgeLayerTables(i);
			for (int j = 0; j < intraEdgeLayer.getSources().size(); j++) {
				// Create row
				CyNode source = addedNodes.get( intraEdgeLayer.getSource(j) );
				CyNode target = addedNodes.get( intraEdgeLayer.getTarget(j) );
				CyRow edgeRow = flattenedNet.getRow( flattenedNet.addEdge( source, target, intraEdgeLayer.getDirection(j) ) );
				// Add layer ID to node names
				String sourceNameWithID =  intraEdgeLayer.getSource(j) + "_" + layerID;
				String targetNameWithID =  intraEdgeLayer.getTarget(j) + "_" + layerID;
				// Fill columns of the row
				edgeRow.set( CyNetwork.NAME, createInteractionValue(sourceNameWithID, targetNameWithID) );
				edgeRow.set( MlnBuilder.WEIGHT, intraEdgeLayer.getWeight(j) );
				edgeRow.set( MlnBuilder.DIRECTION, intraEdgeLayer.getDirection(j) );
				for ( MlnColumn<?> col : intraEdgeLayer.getOtherColumns() ) edgeRow.set( col.getName(), col.get(j) );
				// Identify origin of the edge
				edgeRow.set( LAYER_ID, layerID );
				edgeRow.set( EDGE_LABEL, "intra-layer" );
			}
		}
		
		//		Fill inter-layer edges
		for (int i = 0; i < nbLayers - 1; i++) {
			int layerID_1 = i + 1;
			int layerID_2 = i + 2;
			EdgeLayer interEdgeLayer = mlnData.getInterEdgeLayerTables(i);
			for (int j = 0; j < interEdgeLayer.getSources().size(); j++) {
				// Add layer ID to node names
				String sourceNameWithID =  interEdgeLayer.getSource(j) + "_" + layerID_1;
				String targetNameWithID =  interEdgeLayer.getTarget(j) + "_" + layerID_2;
				// Create row
				CyNode source = getNodeLayer( flattenedNet, layerID_1, sourceNameWithID );
				CyNode target = getNodeLayer( flattenedNet, layerID_2, targetNameWithID );
				CyRow edgeRow = flattenedNet.getRow( flattenedNet.addEdge( source, target, interEdgeLayer.getDirection(j) ) );
				// Fill columns of the row
				edgeRow.set( CyNetwork.NAME, createInteractionValue(sourceNameWithID, targetNameWithID) );
				edgeRow.set( MlnBuilder.WEIGHT, interEdgeLayer.getWeight(j) );
				edgeRow.set( MlnBuilder.DIRECTION, interEdgeLayer.getDirection(j) );
				for ( MlnColumn<?> col : interEdgeLayer.getOtherColumns() ) edgeRow.set( col.getName(), col.get(j) );
				// Identify origin of the edge
				edgeRow.set( LAYER_ID, layerID_1 );
				edgeRow.set( EDGE_LABEL, "inter-layer" );
			}
		}
		
		return flattenedNet;
	}
	
	/*
	 * Create layer-networks from a flattened network.
	 */
	public static CyNetwork[] createLayerNetworksFromFlat( CyNetwork flattenedNet, List<Integer> selectedLayers ) {
		CyTable flatNodeTable = flattenedNet.getTable(CyNode.class, CyNetwork.DEFAULT_ATTRS);
		CyTable flatEdgeTable = flattenedNet.getTable(CyEdge.class, CyNetwork.DEFAULT_ATTRS);
		CyRootNetwork root = ( (CySubNetwork) flattenedNet ).getRootNetwork();
		
		int nbLayers = selectedLayers.size();
		CyNetwork[] layerNetworks = new CyNetwork[nbLayers];
		for (int i = 0; i < nbLayers; i++) {
			int layerID = selectedLayers.get(i);
			//		Create layer-network
			// Create layer-network
			CySubNetwork layerNet = root.addSubNetwork();
			layerNetworks[i] = layerNet;
			// Name the layer
			String layerName = serviceNetworkNaming.getSuggestedNetworkTitle( formatNumberLayer(layerID, nbLayers) + "_Layer" );
			CyTable layerNetTable = layerNet.getTable( CyNetwork.class, CyNetwork.DEFAULT_ATTRS );
			layerNetTable.getRow( layerNet.getSUID() ).set( CyNetwork.NAME, layerName );
			// Define the subnetwork as being a part of a multi-layer network
			if ( layerNetTable.getColumn(LAYER_ID) == null )
				layerNetTable.createColumn( LAYER_ID, Integer.class, false );
			layerNetTable.getRow( layerNet.getSUID() ).set( LAYER_ID, layerID );
			layerNetTable.getRow( layerNet.getSUID() ).set( IS_MLN, true );

			//		Fill the layer-network
			// Add nodes
			for ( CyNode node : flattenedNet.getNodeList() ) {
				int nodeLayerID = flatNodeTable.getRow( node.getSUID() ).get(LAYER_ID, Integer.class);
				if ( nodeLayerID == layerID ) layerNet.addNode( node );
			}
			// Add intra-layer edges
			for ( CyEdge edge : flattenedNet.getEdgeList() ) {
				CyRow row = flatEdgeTable.getRow( edge.getSUID() );
				if ( row.get(LAYER_ID, Integer.class) == layerID && row.get(EDGE_LABEL, String.class).equals("intra-layer") )
					layerNet.addEdge( edge );
			}
		}
		return layerNetworks;
	}
	
	/*
	 * Create inter-layer edge tables from a flattened network
	 */
	public static CyTable[] createInterEdgeTablesFromFlat( CyNetwork flattenedNet, List<Integer> selectedLayers ) {
		CyRootNetwork rootNetwork = ((CySubNetwork)flattenedNet).getRootNetwork(); 
		CyTable rootEdgeTable = rootNetwork.getTable(CyEdge.class, CyNetwork.LOCAL_ATTRS);
		
		int nbLayers = selectedLayers.size();
		CyTable[] interEdgeTables = new CyTable[nbLayers];
		for (int i = 0; i < nbLayers - 1; i++) {
			int layerID = selectedLayers.get(i);
			// Create inter-layer edge table
			CyTable table = serviceTableFactory.createTable( getInterEdgeTableName(layerID, layerID+1),
					CyNetwork.SUID, Long.class, true, true );
			interEdgeTables[i] = table;
			// Add virtual columns
			for ( CyColumn col : rootEdgeTable.getColumns() ) {
				String colName = col.getName();
				if ( table.getColumn( colName ) == null ) 
					table.addVirtualColumn( colName, colName, rootEdgeTable, CyNetwork.SUID, false );
			}
			// Fill the table
			for ( CyEdge edge : flattenedNet.getEdgeList() ) {
				CyRow flatRow = flattenedNet.getRow( edge );
				if ( flatRow.get(LAYER_ID, Integer.class) == layerID && flatRow.get(EDGE_LABEL, String.class).equals("inter-layer") )
					table.getRow( edge.getSUID() );
			}
		}
		return interEdgeTables;
	}

	/*
	 * Create aggregated network from a flattened network.
	 * 
	 * Aggregated network are undirected.
	 */
	public static CyNetwork createAggregatedNetwork( CyNetwork flattenedNet ) {
		CyRootNetwork root = ( (CySubNetwork) flattenedNet ).getRootNetwork();
		CySubNetwork aggNetwork = root.addSubNetwork();
		
		// Name the network
		String aggName = serviceNetworkNaming.getSuggestedNetworkTitle( AGG_NETWORK );
		CyTable sharedNetTable = aggNetwork.getDefaultNetworkTable();
		sharedNetTable.getRow( aggNetwork.getSUID() ).set( CyNetwork.NAME, aggName );
		sharedNetTable.getRow( aggNetwork.getSUID() ).set( IS_MLN, true );
		CyTable localNetTable = aggNetwork.getTable( CyNetwork.class, CyNetwork.LOCAL_ATTRS );
		localNetTable.createColumn( AGG_NETWORK, Boolean.class, false );
		localNetTable.getRow( aggNetwork.getSUID() ).set( AGG_NETWORK, true );
		// Create columns
		CyTable nodeTable = aggNetwork.getTable( CyNode.class, CyNetwork.LOCAL_ATTRS );
		nodeTable.createListColumn( LAYER_ID, Integer.class, false );
		CyTable edgeTable = aggNetwork.getTable( CyEdge.class, CyNetwork.LOCAL_ATTRS );
		edgeTable.createListColumn( LAYER_ID, Integer.class, false );
		
		//		Aggregate layers
		// Create lists of unique elements
		Map<String, List<Integer>> nodesPerLayer = new HashMap<String, List<Integer>>();
		Map<List<String>, List<Integer>> intraEdgesPerLayer = new HashMap<List<String>, List<Integer>>();
		// Get node data
		for ( CyRow rowNode : flattenedNet.getDefaultNodeTable().getAllRows() ) {
			int layerID = rowNode.get( LAYER_ID, Integer.class);
			String nodeName = getNameFromNodeName( rowNode.get( CyNetwork.NAME, String.class ) );
			if ( ! nodesPerLayer.containsKey(nodeName) ) nodesPerLayer.put( nodeName, new ArrayList<Integer>() );
			nodesPerLayer.get( nodeName ).add( layerID );
		}
		// Get intra-layer edge data
		for ( CyEdge cyEdge : flattenedNet.getEdgeList() ) {
			CyRow rowEdge = flattenedNet.getRow(cyEdge);
			if( rowEdge.get(EDGE_LABEL, String.class).equals("intra-layer") ) {
				int layerID = rowEdge.get( LAYER_ID, Integer.class);
				String sourceName = flattenedNet.getRow( cyEdge.getSource() ).get( CyNetwork.NAME, String.class );
				String targetName = flattenedNet.getRow( cyEdge.getTarget() ).get( CyNetwork.NAME, String.class );
				List<String> edge = Arrays.asList( getNameFromNodeName(sourceName), getNameFromNodeName(targetName) );
				Collections.sort( edge );
				if ( ! intraEdgesPerLayer.containsKey(edge) ) intraEdgesPerLayer.put( edge, new ArrayList<Integer>() );
				intraEdgesPerLayer.get( edge ).add( layerID );
			}
		}
		
		//		Fill the network
		// Add nodes
		Map<String, CyNode> addedNodes = new HashMap<String, CyNode>();
		for ( String nodeName : nodesPerLayer.keySet() ) {
			CyNode node = aggNetwork.addNode();
			addedNodes.put(nodeName, node);
			aggNetwork.getRow(node).set( CyNetwork.NAME, nodeName );
			aggNetwork.getRow(node).set( LAYER_ID, nodesPerLayer.get(nodeName) );
		}
		// Add intra-layer edges
		for (List<String> edgeLayers : intraEdgesPerLayer.keySet() ) {
			// Get nodes
			String sourceName =  edgeLayers.get(0);
			String targetName = edgeLayers.get(1);
			CyNode source = addedNodes.get(sourceName);
			CyNode target = addedNodes.get(targetName);
			// Add edge
			CyEdge edge = aggNetwork.addEdge(source, target, false);
			aggNetwork.getRow(edge).set( CyNetwork.NAME, createInteractionValue(sourceName, targetName) );
			aggNetwork.getRow(edge).set( LAYER_ID, intraEdgesPerLayer.get(edgeLayers) );
		}
		
		return aggNetwork;
	}
	
	/*
	 * Create multi-layer network collection from a flattened network.
	 * @param flattened network used to generate the MLN
	 * @param list of layer IDs of the MLN
	 */
	public static void createMLNFromFlatNetwork( CyNetwork flattenedNetwork, List<Integer> selectedLayers ) {
		// Create aggregated network
		CyNetwork extractedAggNetwork = MlnWriter.createAggregatedNetwork( flattenedNetwork );
		serviceNetworkManager.addNetwork(extractedAggNetwork);
		// Create layer-networks
		CyNetwork[] extractedLayerNetworks = MlnWriter.createLayerNetworksFromFlat( flattenedNetwork, selectedLayers );
		CyTable[] interEdgeTables = MlnWriter.createInterEdgeTablesFromFlat( flattenedNetwork, selectedLayers );
		for (int i = 0; i < selectedLayers.size(); i++) {
			int layerID = selectedLayers.get(i);
			serviceNetworkManager.addNetwork( extractedLayerNetworks[i] );
			String tableName = MlnWriter.getInterEdgeTableName(layerID, layerID+1);
			if ( i < selectedLayers.size() - 1 ) {
				serviceNetTableManager.setTable( extractedLayerNetworks[i], CyEdge.class, tableName, interEdgeTables[i]  );
				serviceNetTableManager.setTable( extractedLayerNetworks[i+1], CyEdge.class, tableName, interEdgeTables[i] );
			}
		}
	}
	
	
	/*
	 * Add a column from the MLN data into the Cytoscape MLN
	 */
	private static void addColFromMlnToCy( CyTable cyTable, MlnColumn<?> col ) {
		if ( cyTable.getColumn(col.getName()) == null )
			cyTable.createColumn( col.getName(), col.getType(), false );
	}
	
	/*
	 * @return name of the inter-layer edge table
	 */
	public static String getInterEdgeTableName( int layerID1, int layerID2 ) {
		return layerID1 +"->"+ layerID2 + "_Inter-Edge";
	}
	
	/*
	 * Format a number "i" to have the same number of characters than the number "numberLayers" 
	 * by adding zeros as prefix.
	 */
	public static String formatNumberLayer(int i, int numberLayers) {
		String charsNbLayers = String.valueOf(numberLayers);
		String charsI = String.valueOf(i);
		if ( charsI.length() < charsNbLayers.length() ) {
			List<String> zeros = Collections.nCopies( charsNbLayers.length() - charsI.length() , "0");
			return String.join( "", zeros ) + charsI;
		}
		else return charsI;
	}
	
	/*
	 * Get the name of a node name, where the name is separated by "_" from the layer ID
	 * @param node name
	 */
	public static String getNameFromNodeName( String nodeName ) {
		String[] elmts = nodeName.split("_");
		String[] name = Arrays.copyOfRange(elmts, 0, elmts.length - 1);
		return String.join("_", name);
	}
	
	/*
	 * Get the CyNode object related to the name of a node-layer.
	 * @param flattenedNetwork: network where the node should be
	 * @param layerID: rank of the layer (start from 1) within the column LAYER_ID
	 * @param nodeName: name of the node to retrieve
	 * @return CyNode corresponding to the given node name
	 */
	public static CyNode getNodeLayer( CyNetwork flattenedNetwork, int layerID, String nodeName ) throws MlnWriterException {
		// Get node table
		CyTable nodeTable = flattenedNetwork.getDefaultNodeTable();
		String primaryKeyColname = nodeTable.getPrimaryKey().getName();
		// Get rows matching with the nodeName
		ArrayList<CyRow> matchingRows = new ArrayList<CyRow>( nodeTable.getMatchingRows( MlnBuilder.NAME, nodeName ) );
		// Get the row corresponding to the layer ID
		ArrayList<CyRow> layerNodeRows = new ArrayList<CyRow>();
		for (CyRow cyRow : matchingRows)
			if ( cyRow.get(LAYER_ID, Integer.class) == layerID ) layerNodeRows.add(cyRow);
		// Return error if there is not exactly one node within the table
		if ( layerNodeRows.size() > 1 ) throw new MlnWriterException( "Node names not unique",
				"Duplicated node names", JOptionPane.ERROR_MESSAGE );
		if ( layerNodeRows.size() == 0 ) throw new MlnWriterException( "Node name is not contained by the layer " + layerID,
				"Node name not found", JOptionPane.ERROR_MESSAGE );
		// Return the node
		Long nodeId = layerNodeRows.get(0).get(primaryKeyColname, Long.class);
		return flattenedNetwork.getNode( nodeId );
	}
	
	/*
	 * Return all CyNode objects related to the name of node-layer, grouped by layer.
	 * @param flattened network
	 * @return list of maps for each layer
	 */
	public static Map<Integer, Map<String, CyNode>> getAllNodeLayers( CyNetwork flattenedNetwork ) throws MlnWriterException {
		Map<Integer, Map<String, CyNode>> nodeLayers = new Hashtable<Integer, Map<String, CyNode>>();
		for ( CyNode node : flattenedNetwork.getNodeList() ) {
			CyRow row = flattenedNetwork.getRow(node);
			int layerID = row.get(LAYER_ID, Integer.class);
			String name = row.get(CyNetwork.NAME, String.class);
			// Create map with Node names -> CyNode
			if( ! nodeLayers.containsKey(layerID) ) nodeLayers.put( layerID, new Hashtable<String, CyNode>() );
			// Add CyNode into the map
			if( ! nodeLayers.get(layerID).containsKey(name) ) nodeLayers.get(layerID).put( name, node );
			// If the node name is already in the map, it means the node name is duplicated
			else throw new MlnWriterException( "Node names not unique",
					"Duplicated node names", JOptionPane.ERROR_MESSAGE );
		}
		return nodeLayers;
	}
	
	public static String createInteractionValue(String sourceName, String targetName) {
		return sourceName + " (interacts with) " + targetName;
	}

	/*_______________________________________
	 * 
	 *			EXCEPTION
	 *_______________________________________
	 */
	
	public static class MlnWriterException extends MlnException{

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public MlnWriterException(String message, String messageTitle, int messageType) {
			super(message, messageTitle, messageType);
		}
		
		public MlnWriterException(String message, String messageTitle, int messageType, Throwable cause) {
			super(message, messageTitle, messageType, cause);
		}
	}
}

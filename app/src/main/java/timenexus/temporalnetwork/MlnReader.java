package timenexus.temporalnetwork;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.JOptionPane;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;

import timenexus.utils.MlnException;
import timenexus.utils.ServiceProvider;

/*
 * This class can transform a multi-layer network represented as a collection of
 * networks and tables within Cytoscape into a MlnBuilder object.
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
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public final class MlnReader {
	
	private static CyNetworkManager serviceNetworkManager = ServiceProvider.get(CyNetworkManager.class); 
	
	private MlnReader(){ throw new RuntimeException(); }
	
	/*_______________________________________
	 * 
	 *			PROCESS CYTOSCAPE OBJECTS
	 *_______________________________________
	 */
	
	/*
	 * @return multi-layer networks which are available
	 */
	public static List<CyRootNetwork> getMultiLayerNetworks() {
		// Search networks related to multi-layer networks
		HashSet<CyRootNetwork> availableMLN = new HashSet<CyRootNetwork>();
		for ( CyNetwork layerNet : serviceNetworkManager.getNetworkSet() ) {
			CyRow networkRow = layerNet.getDefaultNetworkTable().getRow( layerNet.getSUID() );
			if ( networkRow.get( MlnWriter.IS_MLN, Boolean.class ) != null
					&& networkRow.get( MlnWriter.IS_MLN, Boolean.class ) ) {
				CyRootNetwork rootNetwork = ( (CySubNetwork) layerNet ).getRootNetwork(); 
				availableMLN.add( rootNetwork );
			}
		}
		return new ArrayList<CyRootNetwork>( availableMLN );
	}
	
	/*
	 * Get multi-layer network image from root network of a multi-layer network collection.
	 * @param networkType: MlnWriter.AGG_NETWORK or MlnWriter.FLAT_NETWORK
	 * @param networkTypeName: "aggregated network" or "flattened network" 
	 */
	public static CySubNetwork getMlnImageFromRoot( String networkType, String networkTypeName, CyRootNetwork root )
			throws MlnReaderException {
		if ( root == null )
			throw new MlnReaderException( "No multi-layer network was found.",
					"Multi-layer network not found", JOptionPane.ERROR_MESSAGE );
		CySubNetwork netImage = null;
		int i = 0;
		for ( CySubNetwork net : root.getSubNetworkList() ) {
			if ( checkMlnColumn(net, networkType) ) {
				netImage = net;
				i = i + 1;
			}
		}
		if ( i == 0 )
			throw new MlnReaderException( "No network was recognized as "+networkTypeName+" within the collection " + root + ".",
					"No available " + networkTypeName, JOptionPane.ERROR_MESSAGE );
		else if ( i > 1 )
			throw new MlnReaderException( "There are more than 1 "+ networkTypeName +" within the multi-layer network " + root + ".",
					"No unique " + networkTypeName, JOptionPane.ERROR_MESSAGE );
		return netImage;
	}
	
	/*
	 * @return get all layers of the CyRootNetwork
	 */
	public static TreeMap<Integer, CySubNetwork> getLayersFromMultiLayerNetwork( CyRootNetwork selectedMln ) throws MlnReaderException {
		//		Throw exception if there is no MLN
		if ( selectedMln == null )
			throw new MlnReaderException(
					 "No multi-layer networks are available.\n\n"
					 + "Multi-layer network should be a collection of subnetworks whose some have\n"
					 + "a column '"+MlnWriter.IS_MLN+"' and a column '"+MlnWriter.LAYER_ID+"' within the network table.",
					 "No available multi-layer networks",
					 JOptionPane.WARNING_MESSAGE );
		//		Get all layers of the MLN
		TreeMap<Integer, CySubNetwork> mlnLayers = new TreeMap<Integer, CySubNetwork>();
		for (CySubNetwork layerNet : selectedMln.getSubNetworkList()) {
			CyRow net = layerNet.getDefaultNetworkTable().getRow( layerNet.getSUID() );
			if ( net.get( MlnWriter.IS_MLN, Boolean.class ) != null
					&& net.get( MlnWriter.IS_MLN, Boolean.class )
					&& net.get( MlnWriter.LAYER_ID, Integer.class ) != null )
				mlnLayers.put( net.get( MlnWriter.LAYER_ID, Integer.class ), layerNet );
				
		}
		return mlnLayers;
	}
	
	
	/*
	 * Check if the multi-layer network has an appropriate format.
	 * @throw MlnReaderException
	 */
	public static void checkMultiLayerNetworkFormat( TreeMap<Integer, CySubNetwork> mlnLayers ) throws MlnReaderException{
		//		Throw exception if there is no MLN
		if ( mlnLayers == null || mlnLayers.isEmpty() )
			throw new MlnReaderException(
					 "No network-layers were identified within the selected multi-layer network.\n\n"
					 + "Multi-layer network should be a collection of subnetworks whose some have\n"
					 + "a column '"+MlnWriter.IS_MLN+"' and a column '"+MlnWriter.LAYER_ID+"' within the network table.",
					 "No layers within the multi-layer network",
					 JOptionPane.ERROR_MESSAGE );
		//		Test consistency layer IDs
		int i = 1;
		for ( int id : mlnLayers.keySet() ) {
			if ( id != i ) 
				throw new MlnReaderException(
						 "The layer ID '"+ id +"' is expected to be '"+ i +"'.\n\n"
						 + "Layer ID must be such as an ID of '1' means that the layer is the 1st layer"
						 + " which is directly followed by the layers 2, 3 and so on,\n"
						 + "until the last layer, without gaps. This way, the IDs represent the ranking of layers.",
						 "Naming convention of layer IDs is not respected",
						 JOptionPane.ERROR_MESSAGE );
			i = i + 1;
		}
		//		Check consistency inter-layer edge table IDs
		i = 1;
		for ( int id : mlnLayers.keySet() ) {
			String expectedName1 = MlnWriter.getInterEdgeTableName( i-1, i );
			String expectedName2 = MlnWriter.getInterEdgeTableName( i, i+1 );
			// if this condition is true, there is an error
			if ( ( i > 1 && mlnLayers.get(id).getTable( CyEdge.class, expectedName1 ) == null )
					|| ( i < mlnLayers.size() && mlnLayers.get(id).getTable( CyEdge.class, expectedName2 ) == null ) ) {
				String expectedName;
				if( i == 1 ) expectedName = expectedName2;
				else if( i == mlnLayers.size() ) expectedName = expectedName1;
				else expectedName = expectedName1 + " and " + expectedName2;
				throw new MlnReaderException(
						 "No inter-layer edge table for the layer ID '"+ id +"' was found, while it is expected to be '"+ expectedName +"'.\n\n"
						 + "Names of inter-layer edge tables must follow the rule: [layer N]->[layer N+1]_Inter-Edge Table.",
						 "Naming convention of the inter-layer edge table is not respected",
						 JOptionPane.ERROR_MESSAGE );
			}
			i = i + 1;
		}
		//		Check if the tables have the expected columns
		i = 1;
		for ( int id : mlnLayers.keySet() ) {
			// Node and edge table
			ArrayList<ArrayList<String>> cols = getMainColumnsWithProblems( mlnLayers.get(id) );
			ArrayList<String> missingCols = cols.get(0), invalidCols = cols.get(1);
			// Inter-layer edge table 
			if ( i < mlnLayers.size() ) {
				CyTable interEdgeTable = mlnLayers.get(id).getTable( CyEdge.class, MlnWriter.getInterEdgeTableName( i, i+1 ) );
				if ( interEdgeTable.getColumn(MlnBuilder.NAME) == null ) missingCols.add("'"+MlnBuilder.NAME+"' in inter-layer edge table");
				if ( interEdgeTable.getColumn(MlnBuilder.WEIGHT) == null ) missingCols.add("'"+MlnBuilder.WEIGHT+"' in inter-layer edge table");
				else if ( interEdgeTable.getColumn(MlnBuilder.WEIGHT).getType() != Double.class )
					invalidCols.add("'"+MlnBuilder.WEIGHT+"' in inter-layer edge table is not of type 'Double'");
				if ( interEdgeTable.getColumn(MlnBuilder.DIRECTION) == null ) missingCols.add("'"+MlnBuilder.DIRECTION+"' in inter-layer edge table");
				else if ( interEdgeTable.getColumn(MlnBuilder.DIRECTION).getType() != Boolean.class )
					invalidCols.add("'"+MlnBuilder.DIRECTION+"' in inter-layer edge table is not of type 'Boolean'");
			}
			// Throw error
			if ( ! missingCols.isEmpty() )
				throw new MlnReaderException(
						"The following columns were not found within the layer "+ id +":\n\n"
						+ Arrays.toString(missingCols.toArray()),
						"Format of Cytoscape tables related to layers is not valid",
						JOptionPane.ERROR_MESSAGE );
			else if ( ! invalidCols.isEmpty() )
				throw new MlnReaderException(
						"The following columns do not have a valid type within the layer "+ id +":\n\n"
						+ Arrays.toString(invalidCols.toArray()),
						"Format of Cytoscape tables related to layers is not valid",
						JOptionPane.ERROR_MESSAGE );
			i = i + 1;
		}
	}
	
	/*
	 * Check format of the flattened network.
	 */
	public static void checkFlattenedNetworkFormat( CyNetwork flattenedNet ) throws MlnReaderException {
		//		Check network table
		if ( ! checkMlnColumn(flattenedNet, MlnWriter.FLAT_NETWORK) ) {
			throw new MlnReaderException(
					"Current network was not recognized as flattened network.\n\n"
					+ "Flattened network should have a network table with the boolean columns "
					+ "'"+MlnWriter.IS_MLN+"' and '"+MlnWriter.FLAT_NETWORK+"' set as 'true'.",
					"Unknown flattened network",
					JOptionPane.ERROR_MESSAGE );
		}
		//		Check if the tables have the expected columns
		// Main columns
		ArrayList<ArrayList<String>> mainCols = getMainColumnsWithProblems( flattenedNet );
		ArrayList<String> missingCols = mainCols.get(0),
				invalidCols = mainCols.get(1);
		// Specific columns
		if ( missingCols.isEmpty() && invalidCols.isEmpty() ) {
			ArrayList<ArrayList<String>> SpecCols = getMlnImageNetworkColumnsWithProblems( flattenedNet, true );
			missingCols.addAll( SpecCols.get(0) );
			invalidCols.addAll( SpecCols.get(1) );
		}
		// Throw error
		if ( ! missingCols.isEmpty() )
			throw new MlnReaderException(
					"The following columns were not found for the flattened network:\n\n"
					+ Arrays.toString(missingCols.toArray()),
					"Format of Cytoscape tables related to flattened network is not valid",
					JOptionPane.ERROR_MESSAGE );
		else if ( ! invalidCols.isEmpty() )
			throw new MlnReaderException(
					"The following columns do not have a valid type for the flattened network:\n\n"
					+ Arrays.toString(invalidCols.toArray()),
					"Format of Cytoscape tables related to flattened network is not valid",
					JOptionPane.ERROR_MESSAGE );
		/*//		Test consistency layer IDs (removed because it can have 1 node in a layer but no intra-layer edges)
		TreeSet<Integer> nodeTableLayerIds = getLayerIdsFromFlattenedNetwork( flattenedNet.getDefaultNodeTable() );
		TreeSet<Integer> edgeTableLayerIds = getLayerIdsFromFlattenedNetwork( flattenedNet.getDefaultEdgeTable() );
		checkLayerIdConsistency( nodeTableLayerIds, "node table" );
		checkLayerIdConsistency( edgeTableLayerIds, "edge table" );
		if ( ! nodeTableLayerIds.equals(edgeTableLayerIds) )
			throw new MlnReaderException(
					 "Columns '"+MlnWriter.LAYER_ID+"' of the flattened network are expected\n"
					 		+ "to contain the same values between the node and edge table.",
					 "Naming convention of layer IDs is not respected",
					 JOptionPane.ERROR_MESSAGE );*/
	}
	
	/*
	 * Check format of the aggregated network. 
	 */
	public static void checkAggregatedNetworkFormat( CyNetwork aggregatedNet ) throws MlnReaderException {
		//		Check network table
		if ( ! checkMlnColumn(aggregatedNet, MlnWriter.AGG_NETWORK) ) {
			throw new MlnReaderException(
					"Current network was not recognized as aggregated network.\n\n"
					+ "Aggregated network should have a network table with the boolean columns "
					+ "'"+MlnWriter.IS_MLN+"' and '"+MlnWriter.AGG_NETWORK+"' set as 'true'.",
					"Unknown aggregated network",
					JOptionPane.ERROR_MESSAGE );
		}
		//		Check if the tables have the expected columns
		// Main columns
		ArrayList<ArrayList<String>> mainCols = getMainColumnsWithProblems( aggregatedNet );
		ArrayList<String> missingCols = mainCols.get(0),
				invalidCols = mainCols.get(1);
		// Specific columns
		if ( missingCols.isEmpty() && invalidCols.isEmpty() ) {
			ArrayList<ArrayList<String>> SpecCols = getMlnImageNetworkColumnsWithProblems( aggregatedNet, false );
			missingCols.addAll( SpecCols.get(0) );
			invalidCols.addAll( SpecCols.get(1) );
		}
		// Throw error
		if ( ! missingCols.isEmpty() )
			throw new MlnReaderException(
					"The following columns were not found for the aggregated network:\n\n"
					+ Arrays.toString(missingCols.toArray()),
					"Format of Cytoscape tables related to aggregated network is not valid",
					JOptionPane.ERROR_MESSAGE );
		else if ( ! invalidCols.isEmpty() )
			throw new MlnReaderException(
					"The following columns do not have a valid type for the aggregated network:\n\n"
					+ Arrays.toString(invalidCols.toArray()),
					"Format of Cytoscape tables related to aggregated network is not valid",
					JOptionPane.ERROR_MESSAGE );
		/*//		Test consistency layer IDs (removed because it can have 1 node in a layer but no intra-layer edges)
		TreeSet<Integer> nodeTableLayerIds = getLayerIdsFromAggregatedNetwork( aggregatedNet.getDefaultNodeTable() );
		TreeSet<Integer> edgeTableLayerIds = getLayerIdsFromAggregatedNetwork( aggregatedNet.getDefaultEdgeTable() );
		checkLayerIdConsistency( nodeTableLayerIds, "node table" );
		checkLayerIdConsistency( edgeTableLayerIds, "edge table" );
		if ( ! nodeTableLayerIds.equals(edgeTableLayerIds) )
			throw new MlnReaderException(
					 "Columns '"+MlnWriter.LAYER_ID+"' of the aggregated network are expected\n"
					 		+ "to contain the same values between the node and edge table.",
					 "Naming convention of layer IDs is not respected",
					 JOptionPane.ERROR_MESSAGE );*/
	}
	
	/*
	 * Check column existence for a network.
	 */
	public static ArrayList<ArrayList<String>> getMainColumnsWithProblems( CyNetwork network ) {
		ArrayList<String> missingCols = new ArrayList<String>();
		ArrayList<String> invalidCols = new ArrayList<String>();
		// Node table
		CyTable nodeTable = network.getDefaultNodeTable();
		if ( nodeTable.getColumn(MlnBuilder.NAME) == null ) missingCols.add("'"+MlnBuilder.NAME+"' in node table");
		else if ( nodeTable.getColumn(MlnBuilder.NAME).getType() != String.class )
			invalidCols.add("'"+MlnBuilder.NAME+"' in node table is not of type 'String'");
		if ( nodeTable.getColumn(MlnBuilder.WEIGHT) == null ) missingCols.add("'"+MlnBuilder.WEIGHT+"' in node table");
		else if ( nodeTable.getColumn(MlnBuilder.WEIGHT).getType() != Double.class )
			invalidCols.add("'"+MlnBuilder.WEIGHT+"' in node table is not of type 'Double'");
		// Edge table 
		CyTable edgeTable = network.getDefaultEdgeTable();
		if ( edgeTable.getColumn(MlnBuilder.NAME) == null ) missingCols.add("'"+MlnBuilder.NAME+"' in (intra-layer) edge table");
		else if ( edgeTable.getColumn(MlnBuilder.NAME).getType() != String.class )
			invalidCols.add("'"+MlnBuilder.NAME+"' in (intra-layer) edge table is not of type 'String'");
		if ( edgeTable.getColumn(MlnBuilder.WEIGHT) == null ) missingCols.add("'"+MlnBuilder.WEIGHT+"' in (intra-layer) edge table");
		else if ( edgeTable.getColumn(MlnBuilder.WEIGHT).getType() != Double.class )
			invalidCols.add("'"+MlnBuilder.WEIGHT+"' in (intra-layer) edge table is not of type 'Double'");
		if ( edgeTable.getColumn(MlnBuilder.DIRECTION) == null ) missingCols.add("'"+MlnBuilder.DIRECTION+"' in (intra-layer) edge table");
		else if ( edgeTable.getColumn(MlnBuilder.DIRECTION).getType() != Boolean.class )
			invalidCols.add("'"+MlnBuilder.DIRECTION+"' in (intra-layer) edge table is not of type 'Boolean'");
		// Return
		ArrayList<ArrayList<String>> cols = new ArrayList<ArrayList<String>>();
		cols.add(missingCols);
		cols.add(invalidCols);
		return cols;
	}
	
	/*
	 * Check column existence for MLN-image networks (aggregated and flattened networks).
	 */
	private static ArrayList<ArrayList<String>> getMlnImageNetworkColumnsWithProblems( CyNetwork network, boolean isFlattenedNetwork )
			throws MlnReaderException {
		ArrayList<String> missingCols = new ArrayList<String>();
		ArrayList<String> invalidCols = new ArrayList<String>();
		// Specific columns to Node table
		CyTable nodeTable = network.getDefaultNodeTable();
		if ( nodeTable.getColumn(MlnWriter.LAYER_ID ) == null ) missingCols.add("'"+MlnWriter.LAYER_ID+"' in node table");
		else if ( ( isFlattenedNetwork && nodeTable.getColumn(MlnWriter.LAYER_ID).getType() != Integer.class )
				|| ( ! isFlattenedNetwork && nodeTable.getColumn(MlnWriter.LAYER_ID).getListElementType() != Integer.class ) )
			invalidCols.add("'"+MlnWriter.LAYER_ID+"' in node table is not of type 'Integer'");
		// Specific columns to Edge table 
		CyTable edgeTable = network.getDefaultEdgeTable();
		if ( edgeTable.getColumn(MlnWriter.LAYER_ID ) == null ) missingCols.add("'"+MlnWriter.LAYER_ID+"' in edge table");
		else if ( ( isFlattenedNetwork && edgeTable.getColumn(MlnWriter.LAYER_ID).getType() != Integer.class )
				|| ( ! isFlattenedNetwork && edgeTable.getColumn(MlnWriter.LAYER_ID).getListElementType() != Integer.class ) )
			invalidCols.add("'"+MlnWriter.LAYER_ID+"' in edge table is not of type 'Integer'");
		// Check expected column for flattened network
		if (isFlattenedNetwork) {
			if ( edgeTable.getColumn(MlnWriter.EDGE_LABEL ) == null ) missingCols.add("'"+MlnWriter.EDGE_LABEL+"' in edge table");
			else if ( edgeTable.getColumn(MlnWriter.EDGE_LABEL).getType() != String.class )
				invalidCols.add("'"+MlnWriter.EDGE_LABEL+"' in edge table is not of type 'String'");
			// Test if all values are defined
			List<String> values = edgeTable.getColumn(MlnWriter.EDGE_LABEL).getValues(String.class);
			while (values.remove(null)) { } 
			if ( values.size() != edgeTable.getRowCount() )
				throw new MlnReaderException(
						 "Column '"+MlnWriter.EDGE_LABEL+"' of the flattened network is expected to have a defined value in each cell.",
						 "Naming convention of edge labels is not respected",
						 JOptionPane.ERROR_MESSAGE );
			// Test if only "intra-layer" and "inter-layer" are defined
			HashSet<String> setValues = new HashSet<String>(values);
			setValues.remove("intra-layer");
			setValues.remove("inter-layer");
			if ( setValues.size() != 0 )
				throw new MlnReaderException(
						 "Column '"+MlnWriter.EDGE_LABEL+"' of the flattened network should only contain the values 'inter-layer' or 'intra-layer'.",
						 "Naming convention of edge labels is not respected",
						 JOptionPane.ERROR_MESSAGE );
		}
		// Return
		ArrayList<ArrayList<String>> cols = new ArrayList<ArrayList<String>>();
		cols.add(missingCols);
		cols.add(invalidCols);
		return cols;
	}
	
	/*
	 * Check that node names are unique for MlnLayers
	 * @throw MlnReaderException
	 */
	public static void checkUniqueNodeNameForMlnLayers( TreeMap<Integer, CySubNetwork> mlnLayers ) throws MlnReaderException{
		for ( int id : mlnLayers.keySet() ) {
			CyNetwork layerNet = mlnLayers.get(id);
			CyTable nodeTable = layerNet.getDefaultNodeTable();
			// Get node names
			HashSet<String> names = new HashSet<String>();
			for ( CyNode node : layerNet.getNodeList() ) {
				String nodeName = (String) nodeTable.getRow( node.getSUID() ).get( MlnBuilder.NAME, Object.class );
				names.add(nodeName);
			}
			// If there are duplicated node names, return an error
			if ( names.size() != layerNet.getNodeCount() )
				throw new MlnReaderException(
						"Some node names are duplicated within the layer "+ id +".\n"
						+ "It is expected that the column '"+MlnBuilder.NAME+"' has unique elements.",
						"Node names are duplicated",
						JOptionPane.ERROR_MESSAGE );
		}
	}
	
	/*
	 * Check that node names are unique for flattened network
	 * @throw MlnReaderException
	 */
	public static void checkUniqueNodeNameForFlattenedNetwork( CyNetwork flattenedNet ) throws MlnReaderException{
		CyTable nodeTable = flattenedNet.getDefaultNodeTable();
		// Create lists containing the nodes
		Map<Integer, List<String>> namesPerLayer = new HashMap<Integer, List<String>>();
		TreeSet<Integer> layerIDs = getLayerIdsFromFlattenedNetwork(nodeTable);
		for ( int id : layerIDs ) namesPerLayer.put( id, new ArrayList<String>() );
		// Check that no node names are duplicated
		for ( CyRow row : nodeTable.getAllRows() ) {
			String nodeName = row.get( MlnBuilder.NAME, String.class );
			int id = row.get( MlnWriter.LAYER_ID, Integer.class );
			if ( ! namesPerLayer.get(id).contains(nodeName) ) namesPerLayer.get(id).add(nodeName);
			else
				throw new MlnReaderException(
						"The node name '"+ nodeName +"' is duplicated within the layer "+ id +" of the flattened network.\n"
						+ "It is expected that the column '"+MlnBuilder.NAME+"' has unique elements. Please check the node table.",
						"Duplicated node names",
						JOptionPane.ERROR_MESSAGE );
		}
	}
	
	/*
	 * Check that node names are unique for flattened network
	 * @throw MlnReaderException
	 */
	public static void checkUniqueNodeNameForAggregatedNetwork( CyNetwork aggregatedNet ) throws MlnReaderException {
		ArrayList<String> names = new ArrayList<String>();
		for ( CyNode node : aggregatedNet.getNodeList() ) {
			String nodeName = aggregatedNet.getRow(node).get(CyNetwork.NAME, String.class);
			if ( ! names.contains(nodeName) ) names.add(nodeName);
			else
				throw new MlnReaderException(
						"The node name "+ nodeName +" is duplicated within the aggregated network, regardless the layer.\n"
						+ "It is expected that the column '"+MlnBuilder.NAME+"' has unique elements. Please check the node table.",
						"Duplicated node names",
						JOptionPane.ERROR_MESSAGE );
		}
	}
	
	/*
	 *  @return a multi-layer network object from MlnBuilder
	 */
	
	public static MlnBuilder buildMultiLayerNetwork( TreeMap<Integer, CySubNetwork> mlnLayers ) throws Exception {
		//		Check MLN format
		checkMultiLayerNetworkFormat(mlnLayers);
		checkUniqueNodeNameForMlnLayers(mlnLayers);
		//		Construct MlnBuilder object
		MlnBuilder mlnNetwork = new MlnBuilder( mlnLayers.size() );
		for ( int layerId : mlnLayers.keySet() ) {
			// Node table
			CyTable nodeTable = mlnLayers.get(layerId).getDefaultNodeTable();
			for ( CyColumn cyCol : nodeTable.getColumns() ) {
				String colName = cyCol.getNameOnly();
				Class<?> colType = cyCol.getType();
				if ( colName.equals(MlnBuilder.NAME) )
					mlnNetwork.addNodeColumn( MlnBuilder.NODE, layerId - 1, convertNodeNames( cyCol.getValues( colType ) ) );
					//addObjectColumn( nodeTable, nodeLayer.getNodes(), colName, colType );
				else if ( colName.equals(MlnBuilder.WEIGHT) )
					mlnNetwork.addWeight( MlnBuilder.NODE, layerId - 1, cyCol.getValues( Double.class ) );
				else if ( ! colName.equals("SUID") && ! colName.equals("Shared name") && ! colName.equals("selected") ) {
					mlnNetwork.addOtherColum( MlnBuilder.NODE, layerId - 1, colName, colType, cyCol.getValues( colType ) );
				}
			}
			// Intra-edge table
			CyTable intraEdgeTable = mlnLayers.get(layerId).getDefaultEdgeTable();
			addColumnsToEdgeLayer( MlnBuilder.INTRA_EDGE, mlnNetwork, intraEdgeTable, layerId - 1 );
			// Inter-edge table
			if (layerId < mlnLayers.size() ) {
				CyTable interEdgeTable = mlnLayers
						.get( layerId )
						.getTable( CyEdge.class, MlnWriter.getInterEdgeTableName( layerId, layerId+1 ) );
				addColumnsToEdgeLayer( MlnBuilder.INTER_EDGE, mlnNetwork, interEdgeTable, layerId - 1 );
			}
			
		}
		
		
		return mlnNetwork;
	}
	
	
	/*_______________________________________
	 * 
	 *			UTILS
	 *_______________________________________
	 */
	
	/*
	 * Get layer IDs from flattened network
	 * @param table: CyTable containing the layer IDs within the column MlnWriter.LAYER_ID
	 */
	public static TreeSet<Integer> getLayerIdsFromFlattenedNetwork( CyTable table ) throws MlnReaderException{
		// Get values
		List<Integer> values = table.getColumn(MlnWriter.LAYER_ID).getValues(Integer.class);
		// Check that all values are defined
		if ( values.contains(null) )
			throw new MlnReaderException(
					 "Column '"+MlnWriter.LAYER_ID+"' of the flattened network is expected to have a defined value in each cell.",
					 "Naming convention of layer IDs is not respected",
					 JOptionPane.ERROR_MESSAGE );
		else
			while (values.remove(null)) { } 
		// return
		return new TreeSet<Integer>( values );
	}
	
	/*
	 * Get number of layers from flattened network.
	 * @param CyNetwork of flattened network
	 * @return number of layers
	 */
	public static int getNbOfLayersFromFlattenedNetwork( CyNetwork flattenedNet ) throws MlnReaderException {
		TreeSet<Integer> layerIDs = getLayerIdsFromFlattenedNetwork( flattenedNet.getDefaultNodeTable() );
		return layerIDs.last();
	}
	
	/*
	 * Get layer IDs from aggregated network
	 * @param table: CyTable containing the layer IDs within the column MlnWriter.LAYER_ID
	 */
	public static TreeSet<Integer> getLayerIdsFromAggregatedNetwork( CyTable table ) throws MlnReaderException{
		// Get values
		List<Integer> values = new ArrayList<Integer>();
		for ( CyRow row : table.getAllRows() ) {
			List<Integer> ids = row.getList( MlnWriter.LAYER_ID, Integer.class );
			values.addAll(ids);
		}
		// Check that all values are defined
		if ( values.contains(null) )
			throw new MlnReaderException(
					 "Column '"+MlnWriter.LAYER_ID+"' of the aggregated network is expected to have a defined value in each cell.",
					 "Naming convention of layer IDs is not respected",
					 JOptionPane.ERROR_MESSAGE );
		else
			while (values.remove(null)) {  } 
		// Return
		return new TreeSet<Integer>( values );
	}
	
	/*
	 * Check that layer IDs have the appropriate number.
	 */
	@SuppressWarnings("unused")
	private static void checkLayerIdConsistency( TreeSet<Integer> layerIDs, String tableTitle ) throws MlnReaderException {
		int i = layerIDs.first();
		for (int id : layerIDs) {
			if ( id != i )
				throw new MlnReaderException(
						 "The layer ID '"+ id +"' is expected to be '"+ i +"' within '"+ tableTitle +"'.\n\n"
						 + "Layer ID must be such as an ID of '1' means that the layer is the 1st layer"
						 + " which is directly followed by the layers 2, 3 and so on,\n"
						 + "until the last layer, without gaps. This way, the IDs represent the ranking of layers.",
						 "Naming convention of layer IDs is not respected",
						 JOptionPane.ERROR_MESSAGE );
			i = i + 1;
		}
	}
	
	/*
	 * Test existence of Mln column from subnetwork.
	 * @param col: MlnWriter.FLAT_NETWORK or MlnWriter.AGG_NETWORK
	 */
	public static boolean checkMlnColumn( CyNetwork net, String col ) {
		CyTable table = net.getDefaultNetworkTable();
		return table.getColumn(col) != null
				&& table.getRow(net.getSUID()).get(col, Boolean.class)
				&& table.getColumn(MlnWriter.IS_MLN) != null
				&& table.getRow(net.getSUID()).get(MlnWriter.IS_MLN, Boolean.class);
				
	}
	
	/*
	 * Convert node names into strings.
	 */
	public static Collection<String> convertNodeNames( Collection<Object> nodeNames ){
		List<String> strings = new ArrayList<>(nodeNames.size());
		for (Object object : nodeNames) {
		    strings.add(Objects.toString(object, null));
		}
		return strings;
	}
	
	private static void addColumnsToEdgeLayer( int tableType, MlnBuilder mlnNetwork, CyTable edgeTable, int layer ) throws Exception {
		for ( CyColumn cyCol : edgeTable.getColumns() ) {
			String colName = cyCol.getNameOnly();
			Class<?> colType = cyCol.getType();
			if ( colName.equals(MlnBuilder.SOURCE) )
				mlnNetwork.addSourceColumn( tableType, layer, convertNodeNames( cyCol.getValues( colType ) ) );
			else if ( colName.equals(MlnBuilder.TARGET) )
				mlnNetwork.addTargetColumn( tableType, layer, convertNodeNames( cyCol.getValues( colType ) ) );
			else if ( colName.equals(MlnBuilder.WEIGHT) )
				mlnNetwork.addWeight( tableType, layer, cyCol.getValues( Double.class ) );
			else if ( colName.equals(MlnBuilder.DIRECTION) )
				mlnNetwork.addDirection( tableType, layer, cyCol.getValues( Boolean.class ) );
			else if ( ! colName.equals("SUID") && ! colName.equals("Shared name") && ! colName.equals("selected")
					&& ! colName.equals("Shared interaction") && ! colName.equals("interaction") )
				mlnNetwork.addOtherColum( tableType, layer, colName, colType, cyCol.getValues( colType ) );
		}
	}
	
	/*
	 * Parse the name column of an edge table with values such as "<source-node name> (interacts with) <target-node name>"
	 * @return an ArrayList with first the source-node names and second the target-node names
	 */
	public static ArrayList<Collection<String>> parseInteractColumn( CyColumn col) throws MlnReaderException{
		Collection<String> sources = new ArrayList<String>();
		Collection<String> targets = new ArrayList<String>();
		// Parse the "interacts with" column
		for ( String r : col.getValues( String.class ) ) {
			String[] elmts = parseInteractValue(r);
			sources.add( elmts[0] );
			targets.add( elmts[1] );
		}
		// Export sources and targets 
		ArrayList<Collection<String>> data = new ArrayList<Collection<String>>();
		data.add(sources);
		data.add(targets);
		return data;
	}
	
	/*
	 * 
	 */
	public static String[] parseInteractValue( String row ) throws MlnReaderException {
		String[] elmts = row.split("interacts");
		if ( elmts.length != 2 ) throw new MlnReaderException(
				"Values must be as <source-node name> (interacts with) <target-node name>.",
				"Invalid interaction name", JOptionPane.ERROR_MESSAGE );
		elmts[0] = elmts[0].substring(0, elmts[0].length() - 2);
		elmts[1] = elmts[1].substring(7);
		return elmts;
	}
	
	/*_______________________________________
	 * 
	 *			EXCEPTION
	 *_______________________________________
	 */
	
	public static class MlnReaderException extends MlnException {

		private static final long serialVersionUID = 1L;
		
		public MlnReaderException(String message, String messageTitle, int messageType) {
			super(message, messageTitle, messageType);
		}
		
		public MlnReaderException(String message, String messageTitle, int messageType, Throwable cause) {
			super(message, messageTitle, messageType, cause);
		}
		
	}
	
}

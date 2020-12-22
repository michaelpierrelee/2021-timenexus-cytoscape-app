package timenexus.extraction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.work.Task;

import timenexus.apps.AppCaller;
import timenexus.apps.ExtractedNetwork;
import timenexus.temporalnetwork.MlnWriter;
import timenexus.utils.MlnException;
import timenexus.utils.Print;
import timenexus.utils.ServiceProvider;

/*
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public abstract class ExtractionMethod implements Task {

	// Column for node table such as a node-layer is true if it was used a query
	private static String ISQUERY = "isQuery";
	// Contain the app used to perform the extraction
	protected AppCaller app;
	// Contain the flattened network from the multi-layer network which will be processed
	protected CySubNetwork flattenedNet;
	// Contain the layer IDs to use
	protected List<Integer> layers;
	// Contain the list of temporary networks used by the extracting apps
	protected List<CyNetwork> networksToExtract = new ArrayList<CyNetwork>();
	// Enable to check the multi-layer network
	private boolean isCheckEnabled = true;
	// Enable to cancel the task
	volatile boolean cancelled = false;
	
	/*
	 * Cancel the task.
	 */
	@Override
	public void cancel() { cancelled = true; }
	
	/*
	 * Test if the task is cancelled.
	 */
	public boolean isCancelled() { return cancelled; }
	
	/*
	 * Throw an error is the task is cancelled.
	 */
	public void checkCancelling() throws MlnExtractionException {
		if ( isCancelled() ) {
			// Reset temporary networks
			CyNetworkManager manager = ServiceProvider.get(CyNetworkManager.class);
			for (CyNetwork net : networksToExtract) manager.destroyNetwork(net);
			networksToExtract = new ArrayList<CyNetwork>();
			// Reset canceling
			cancelled = false;
			// Stop the task
			throw new MlnExtractionException("The extraction was cancelled",
				"Extraction cancelled", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	/*
	 * @param extracting app to use
	 */
	public void setApp( AppCaller app ) { this.app = app; }
	
	/*
	 * @param flattened network to which subnetworks will be extracted
	 */
	public void setFlattenedNet( CySubNetwork flattenedNet ) { this.flattenedNet = flattenedNet; }
	
	/*
	 * @param list of layers used for the extraction
	 */
	public void setLayers( List<Integer> layers ) { this.layers = layers; }

	/*
	 * Check that list of layers has adjacent layers without gaps.
	 * @param list of layer IDs
	 */
	protected void checkListOfLayers( List<Integer> layers ) throws MlnExtractionException {
		for ( int k = 0; k < layers.size() - 1; k++ ) {
			int idLayer1 = layers.get(k);
			int idLayer2 = layers.get(k+1);
			if ( idLayer2 != idLayer1 + 1 )
				throw new MlnExtractionException( "Pairwise extraction cannot work if the list of layers is not continuous",
						"Extraction failure", JOptionPane.ERROR_MESSAGE );
		}
	}
	
	/*
	 * If the the multi-layer network doesn't fit the app's criteria,
	 * then either the operation is aborted or an automatic conversion can be performed by the app. 
	 */
	protected void checkMultiLayerNetwork() throws MlnExtractionException {
		String message = app.checkNetwork(flattenedNet);
		if ( message != null ) {
			Print.messageDialog("TimeNexus extraction",
					"The multi-layer network does not meet the following criteria of the extracting app.\n"
					+ "It will be updated according to these criteria.\n\n"
					+ message,
					JOptionPane.WARNING_MESSAGE );
		}
	}
	
	/*
	 * Get query nodes for a given layer of the flattened network
	 * @param flattened network
	 * @param layer ID
	 * @param name of the column with query nodes
	 * @return a map such as the keys are the query nodes and, if the column is string, the values are the target nodes of the queries, otherwise the values are empty.
	 */
	protected Map<String, String> getQueryNodesFromLayer( CyNetwork flattenedNet, int layerID, String queryColName ) {
		Map<String, String> queries = new HashMap<String, String>();
		CyColumn col = flattenedNet.getDefaultNodeTable().getColumn(queryColName);
		// If the column doesn't exist (= the user did not select it), then return empty object
		if ( col != null ) {
			Class<?> colType = col.getType();
			// Get query nodes
			for (CyNode cyNode : flattenedNet.getNodeList()) {
				// Get the row
				CyRow row = flattenedNet.getRow( cyNode );
				int id = row.get( MlnWriter.LAYER_ID, Integer.class );
				// Add the query node and, if needed, its value to the map
				Object queryValue = row.get(queryColName, colType);
				if ( id == layerID && queryValue != null ) {
					String queryNodeName = flattenedNet.getRow( cyNode ).get( CyNetwork.NAME, String.class );
					// the column is boolean and so, no values are needed
					if ( colType.isAssignableFrom(Boolean.class) && (Boolean) queryValue )
						queries.put( queryNodeName, null );
					// the column is string, so it means a link between the query node to its value
					else if ( colType.isAssignableFrom(String.class) && (String) queryValue != "" )
						queries.put( queryNodeName, (String) queryValue );
				}
			}
		} 
		return queries;
	}
	
	/*
	 * Create a boolean column for the node table where a cell is 'true' if the node-layer was
	 * used as a query or 'false' otherwise.
	 */
	protected void addIsQueryColumn( Set<String> queryNodeLayers, CyNetwork flattenedNet ) {
		CyTable nodeTable = flattenedNet.getDefaultNodeTable();
		if ( nodeTable.getColumn( ISQUERY ) == null ) {
			// Create the column
			nodeTable.createColumn( ISQUERY, Boolean.class, false );
			// Set the values
			for ( CyRow row : nodeTable.getAllRows() ) {
				String nodeName = row.get( CyNetwork.NAME, String.class );
				row.set( ISQUERY, queryNodeLayers.contains(nodeName) );
			}
		}
		else
			Print.messageDialog( "Warning from subnetwork building",
					"A column nammed 'isQuery' already exists in the multilayer network.\n"
					+ "It was not added to the subnetwork.", JOptionPane.WARNING_MESSAGE );
	}
	
	/*
	 * Add nodes and edges of a given layer from a flattened network to another sub network.
	 * Both networks should be belong to the same root network.
	 * @param flattened network
	 * @param another flattened network
	 * @param layer ID of the layer from which nodes and edges 
	 */
	/*protected void addLayerToNetwork( CySubNetwork fromFlatNet, CySubNetwork toFlatNet, int layerID, boolean withInterLayerEdges ) {
		// Add nodes
		for ( CyNode node : fromFlatNet.getNodeList() ) {
			int nodeLayerID = fromFlatNet.getRow( node ).get( MlnWriter.LAYER_ID, Integer.class );
			if ( nodeLayerID == layerID )
				toFlatNet.addNode(node);
		}
		// Add edges
		for ( CyEdge edge : fromFlatNet.getEdgeList() ) {
			int edgeLayerID = fromFlatNet.getRow( edge ).get( MlnWriter.LAYER_ID, Integer.class );
			String label = fromFlatNet.getRow( edge ).get( MlnWriter.EDGE_LABEL, String.class );
			if ( edgeLayerID == layerID && ( label.equals("intra-layer") || withInterLayerEdges ) ) 
				toFlatNet.addEdge(edge);
		}
	}*/
	
	/*
	 * Add the attributes of the extracted network generated by the app, to a flattened network.
	 * 
	 * ANCHOR node from AnatApp is not managed by the TimeNexus, as it is a de-novo node created by AnatApp
	 */
	protected void addAtributesToFlatNetwork( ExtractedNetwork extractedNet, CyNetwork flatNetwork ) {
		// Get nodes from the flattened network
		Map<String, CyNode> flatNodes = new HashMap<String, CyNode>();
		for ( CyNode node : flatNetwork.getNodeList() )
			flatNodes.put( flatNetwork.getRow(node).get(CyNetwork.NAME, String.class), node );
		// Nodes
		CyTable nodeTable = flatNetwork.getDefaultNodeTable();
		for( String attrName : extractedNet.getNodeAttributeNames() ) {
			Class<?> attrType = extractedNet.getNodeAttributeType( attrName );
			List<?> attrValues = extractedNet.getNodeAttributeValues( attrName );
			// Create column
			createAttributeColumns( nodeTable, attrType, attrName, attrValues );
			// Add values
			int nodeID = 0;
			for ( String nodeName : extractedNet.getNodeNames() ) {
				// Do not add node attributes which are not within the original flattened network (such as ANCHOR node from AnatApp
				if ( flatNodes.keySet().contains(nodeName) )
					flatNetwork.getRow( flatNodes.get(nodeName) ).set( attrName, attrValues.get(nodeID) );
				nodeID++;
			}
		}
		// Add attributes to edges 
		CyTable edgeTable = flatNetwork.getDefaultEdgeTable();
		for( String attrName : extractedNet.getEdgeAttributeNames() ) {
			Class<?> attrType = extractedNet.getEdgeAttributeType( attrName );
			List<?> attrValues = extractedNet.getEdgeAttributeValues( attrName );
			// Create column
			createAttributeColumns( edgeTable, attrType, attrName, attrValues );
			// Add values
			int edgeID = 0;
			for ( List<String> edge : extractedNet.getEdges() ) {
				String nodeName1 = edge.get(0);
				String nodeName2 = edge.get(1);
				// Get the nodes related to the edge of the extracted network
				CyRow nodeRow1 = (new ArrayList<CyRow>( nodeTable.getMatchingRows( CyNetwork.NAME, nodeName1 ) ) ).get(0);
				CyRow nodeRow2 = (new ArrayList<CyRow>( nodeTable.getMatchingRows( CyNetwork.NAME, nodeName2 ) ) ).get(0);
				CyNode node1 = flatNetwork.getNode( nodeRow1.get(CyNetwork.SUID, Long.class ) );
				CyNode node2 = flatNetwork.getNode( nodeRow2.get(CyNetwork.SUID, Long.class ) );
				// Get the edge connecting the two nodes within the flattened network 
				List<CyEdge> edges = flatNetwork.getConnectingEdgeList( node1, node2, CyEdge.Type.ANY );
				// Set the attribute value
				for ( CyEdge e : edges ) {
					// Use only the edges going in the Source->Target direction (the app caller should manage the other direction if needed) 
					if ( e.getSource() == node1 ) {
						CyRow edgeRow = flatNetwork.getRow( e );
						edgeRow.set( attrName, attrValues.get(edgeID) );
						edgeID++;
					}
				}
			}
		}
	}
	
	/*
	 * Add columns related to attributes
	 */
	private void createAttributeColumns( CyTable table, Class<?> attrType, String attrName, List<?> attrValues ) {
		if ( table.getColumn(attrName) == null && attrValues.get(0) instanceof List )
			table.createListColumn( attrName, attrType, false );
		else if ( table.getColumn(attrName) == null )
			table.createColumn( attrName, attrType, false );
	}
	
	public boolean isCheckEnabled() {
		return isCheckEnabled;
	}

	public void setCheckEnabled(boolean isCheckEnabled) {
		this.isCheckEnabled = isCheckEnabled;
	}
	
	/*_______________________________________
	 * 
	 *			EXCEPTION
	 *_______________________________________
	 */

	public static class MlnExtractionException extends MlnException{

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public MlnExtractionException(String message, String messageTitle, int messageType) {
			super(message, messageTitle, messageType);
		}
		
		public MlnExtractionException(String message, String messageTitle, int messageType, Throwable cause) {
			super(message, messageTitle, messageType, cause);
		}
	}
}

package timenexus.apps;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CySubNetwork;

import timenexus.extraction.ExtractionMethod;
import timenexus.extraction.ExtractionMethod.MlnExtractionException;
import timenexus.temporalnetwork.MlnBuilder;
import timenexus.temporalnetwork.MlnWriter;

/*
 * Provide methods to process edge directions and multi-edges
 * to fit the criteria of extracting apps.
 * 
 * PathLinker uses the internal edge direction (cyEdge.isDirected()) to process networks.
 * 
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class EdgeManagement {

	private EdgeManagement() {}
	
	/*_______________________________________
	 * 
	 *			EDGE DIRECTION
	 *_______________________________________
	 */
	
	/*
	 * Reset direction of an edge.
	 * @param network of the edge
	 * @param default edge table of the network
	 * @param the edge to reset
	 * @param the row of the edge
	 * @param true if the edge is directed
	 * @return the new cyEdge
	 */
	public static CyEdge resetEdgeDirection( CyNetwork net, CyTable edgeTable, CyEdge cyEdge, CyRow row, boolean toBeDirected ) {
		// Copy the edge
		CyEdge newEdge = net.addEdge( cyEdge.getSource(), cyEdge.getTarget(), toBeDirected );
		CyRow rowNewEdge = net.getRow(newEdge);
		for ( CyColumn col : edgeTable.getColumns() ) {
			if ( ! col.getName().equals(CyNetwork.SUID) )
				rowNewEdge.set( col.getName(), row.get( col.getName(), col.getType() ) );
		}
		// Remove the previous edge
		((CySubNetwork) net).getRootNetwork().removeEdges( List.of(cyEdge) );
		return newEdge;
	}
	
	/*
	 * Set directions for edges of a list.
	 * @param network of the edges
	 * @param list of edges to set
	 * @param direction of the edges (directed = true, undirected = false)
	 * @param extraction method to get any canceling command
	 */
	public static void setEdgeDirection( CyNetwork net, List<CyEdge> edgesToConvert, boolean toBeDirected, ExtractionMethod method )
			throws MlnExtractionException {
		CyTable edgeTable = net.getDefaultEdgeTable();
		for (CyEdge cyEdge : edgesToConvert) {
			// Set the official direction of the edge (which is display in the table
			CyRow row = net.getRow( cyEdge );
			row.set( MlnBuilder.DIRECTION , toBeDirected );
			// Reset the internal direction if it doesn't match with the official direction
			if ( ( toBeDirected && ! cyEdge.isDirected() ) || ( ! toBeDirected && cyEdge.isDirected() ) )
				resetEdgeDirection( net, edgeTable, cyEdge, row, toBeDirected );
			// Cancel
			method.checkCancelling();
		}
	}
	
	/*
	 * Set all the edges of a network as undirected edges.
	 * @param the network to set
	 * @param extraction method to get any canceling command
	 */
	public static void setNetworkAsUndirected( CyNetwork net, ExtractionMethod method ) throws MlnExtractionException {
		setEdgeDirection( net, net.getEdgeList(), false, method );
	}
	
	/*
	 * Set all the edges of a network as directed edges.
	 * Undirected edges will give two same edges but with opposite directions.
	 * @param the network to set
	 * @param extraction method to get any canceling command
	 */
	public static void setNetworkAsDirected( CyNetwork net, ExtractionMethod method ) throws MlnExtractionException {
		CyTable edgeTable = net.getDefaultEdgeTable();
		for ( CyEdge cyEdge : net.getEdgeList() ) {
			CyRow row = net.getRow( cyEdge );
			Boolean isDirected = row.get( MlnBuilder.DIRECTION , Boolean.class );
			if ( isDirected == null || ! isDirected ) {
				// Set the official direction of the edge (which is display in the table)
				row.set( MlnBuilder.DIRECTION , true );
				// Reset the internal direction if it doesn't match with the official direction
				if ( ! cyEdge.isDirected() ) {
					cyEdge = resetEdgeDirection( net, edgeTable, cyEdge, row, true );
					row = net.getRow(cyEdge);
				}
				// Copy the edge and change direction of the copy
				CyEdge oppositeEdge = net.addEdge( cyEdge.getTarget() , cyEdge.getSource(), true);
				CyRow rowOppositeEdge = net.getRow( oppositeEdge );
				for ( CyColumn col : edgeTable.getColumns() )
					rowOppositeEdge.set( col.getName(), row.get( col.getName(), col.getType() ) );
				// Rename the opposite edge
				String sourceName = net.getRow( cyEdge.getTarget() ).get(CyNetwork.NAME, String.class);
				String targetName = net.getRow( cyEdge.getSource() ).get(CyNetwork.NAME, String.class);
				rowOppositeEdge.set( CyNetwork.NAME, MlnWriter.createInteractionValue(sourceName, targetName) );
			}
			// Cancel
			method.checkCancelling();
		}
	}
	
	/*
	 * Get directed intra-layer edges.
	 * @param network
	 * @return list of edges
	 */
	public static List<CyEdge> getDirectedIntraLayerEdges( CyNetwork net ) {
		List<CyEdge> dirIntraEdges = new ArrayList<CyEdge>();
		for (CyEdge cyEdge : net.getEdgeList()) {
			CyRow row = net.getRow( cyEdge );
			Boolean isDirected = row.get( MlnBuilder.DIRECTION , Boolean.class );
			String label = row.get( MlnWriter.EDGE_LABEL , String.class );
			if ( isDirected != null && isDirected && label != null && label.equals("intra-layer") )
				dirIntraEdges.add(cyEdge);
		}
		return dirIntraEdges;
	}
	
	/*_______________________________________
	 * 
	 *			MULTI-EDGES
	 *_______________________________________
	 */
	
	/*
	 * Check if the network has multi-edges, regarless the direction of the edges.
	 */
	public static boolean isNetworkHasMultiEdges( CyNetwork net ) {
		for ( CyNode node : net.getNodeList() ) { 
			List<CyEdge> adjacentEdges = net.getAdjacentEdgeList(node, CyEdge.Type.ANY); 
			Set<CyNode> neighbors = new HashSet<CyNode>( net.getNeighborList(node, CyEdge.Type.ANY) );
			// Test if the node has multi-edges (more edges than neighbors)
			if ( neighbors.size() < adjacentEdges.size() ) return true;
		}
		return false;
	}
	
	/*
	 * Calculate the aggregated weight of edges.
	 * @param network of the edges
	 * @param list of edges to aggregated
	 */
	public static Double calculateAggregatedWeight( CyNetwork net, List<CyEdge> edgesToAggregate ) {
		// Get the weights
		Double weight = 0.;
		for (CyEdge edge : edgesToAggregate) {
			Double w = net.getRow(edge).get(MlnBuilder.WEIGHT, Double.class);
			if ( w != null ) weight += w;
		}
		// Calculate the aggregated weight
		if ( edgesToAggregate.isEmpty() ) return 0.;
		else return weight / edgesToAggregate.size();
	}
	
	/*
	 * Function to use one of the method getting the edges to aggregate.
	 */
	@FunctionalInterface
	interface GetEdgesToAggregate {
	    public List<CyEdge> apply(CyNetwork one, CyNode two, CyNode three);
	}
	
	/*
	 * Aggregate multi-edges into simple edges.
	 * 
	 * Only the weight is aggregated according to the method calculateAggregatedWeight.
	 * For rows of aggregated edges, the other columns are not reset and should be ignored, 
	 * as one edge among the multi-edges is arbitrary kept, while the other edges are removed.
	 * 
	 * Functions are used to get the list of edges to aggregate.
	 * Several functions can be applied in order to process different types of edges.
	 * 
	 * @param network to aggregate
	 * @param list of functions used to get the edges to aggregate
	 * @param list of the directions for the simple-aggregated edge for each function
	 * @param extraction method to get any canceling command
	 */
	private static void aggregateMultiEdges( CyNetwork net, GetEdgesToAggregate[] getEdgesToAggregate, boolean[] isAggregatedEdgeDirected,
			ExtractionMethod method ) throws MlnExtractionException {
		// Get the multi-edges around each node
		for ( CyNode node : net.getNodeList() ) { 
			List<CyEdge> adjacentEdges = net.getAdjacentEdgeList(node, CyEdge.Type.ANY); 
			Set<CyNode> neighbors = new HashSet<CyNode>( net.getNeighborList(node, CyEdge.Type.ANY) );
			// Test if the node has multi-edges (more edges than neighbors)
			if ( neighbors.size() < adjacentEdges.size() ) {
				//Print.out( net.getRow(node).getRaw(CyNetwork.NAME) );
				for (CyNode neighbor : neighbors) {
					// Successively apply the edge aggregation methods
					for (int j = 0; j < getEdgesToAggregate.length; j++) {
						// Get edges to aggregate
						List<CyEdge> multiEdges = getEdgesToAggregate[j].apply(net, node, neighbor);
						// Aggregate the edges
						if ( multiEdges.size() > 1 ) {
							// Calculate the aggregated weight
							double weight = EdgeManagement.calculateAggregatedWeight(net, multiEdges);
							// Update one of the edge with the aggregated weight
							CyEdge aggEdge = multiEdges.get(0);
							net.getRow(aggEdge).set(MlnBuilder.WEIGHT, weight);
							// Set direction of the edge
							setEdgeDirection( net, List.of(aggEdge), isAggregatedEdgeDirected[j], method );
							// Remove the others edges
							((CySubNetwork) net).getRootNetwork().removeEdges( multiEdges.subList( 1, multiEdges.size() ) );
						}
					}
				}
			}
			// Cancel
			method.checkCancelling();
		}
	}
	
	/*
	 * Aggregate undirected multi-edges into simple edges. Only the weight is aggregated.
	 * @param network to aggregate
	 * @param extraction method to get any canceling command
	 */
	public static void aggregateUndirectedMultiEdges( CyNetwork net, ExtractionMethod method ) throws MlnExtractionException {
		GetEdgesToAggregate[] getEdgesToAggregate = { EdgeManagement::getUndirectedMultiEdges };
		boolean[] isAggregatedEdgeDirected = { false };
		aggregateMultiEdges( net, getEdgesToAggregate, isAggregatedEdgeDirected, method );
	}
	
	/*
	 * Aggregate directed multi-edges, which are going in the same direction,
	 * into simple edges. Only the weight is aggregated.
	 * @param network to aggregate
	 * @param extraction method to get any canceling command
	 */
	public static void aggregateIdenticallyDirectedMultiEdges( CyNetwork net, ExtractionMethod method ) throws MlnExtractionException {
		GetEdgesToAggregate[] getEdgesToAggregate = { EdgeManagement::getIncomingDirectedMultiEdges,
				EdgeManagement::getOutgoingDirectedMultiEdges };
		boolean[] isAggregatedEdgeDirected = { true, true };
		aggregateMultiEdges( net, getEdgesToAggregate, isAggregatedEdgeDirected, method );
	}
	
	/*
	 * Aggregate multi-edges to have only simple edges.
	 * Only the weight is aggregated.
	 * @param network to aggregate
	 * @param extraction method to get any canceling command
	 */
	public static void aggregateMixedMultiEdges( CyNetwork net, ExtractionMethod method ) throws MlnExtractionException {
		GetEdgesToAggregate[] getEdgesToAggregate = { EdgeManagement::getMixedMultiEdges,
				EdgeManagement::getOppositeDirectedMultiEdges,
				EdgeManagement::getIncomingDirectedMultiEdges,
				EdgeManagement::getOutgoingDirectedMultiEdges };
		boolean[] isAggregatedEdgeDirected = { false, false, true, true };
		aggregateMultiEdges( net, getEdgesToAggregate, isAggregatedEdgeDirected, method );
	}
	
	/*
	 * Get the undirected edges of a node pair.
	 * @param network of the nodes
	 * @param first node of the node pair
	 * @param second node of the node pair
	 * @return the list of edges
	 */
	public static List<CyEdge> getUndirectedMultiEdges( CyNetwork net, CyNode node, CyNode neighbor ) {
		List<CyEdge> undirectedEdges = new ArrayList<CyEdge>();
		// Get the edges connecting the node pair
		for ( CyEdge cyEdge : net.getConnectingEdgeList( node, neighbor, CyEdge.Type.ANY ) )
			if ( ! net.getRow(cyEdge).get(MlnBuilder.DIRECTION, Boolean.class) )
				undirectedEdges.add(cyEdge);
		return undirectedEdges;
	}
	
	/*
	 * Get all the multi-edges but with at least one undirected edge.
	 * @param network of the nodes
	 * @param first node of the node pair
	 * @param second node of the node pair
	 * @return the list of edges
	 */
	public static List<CyEdge> getMixedMultiEdges( CyNetwork net, CyNode node, CyNode neighbor ) {
		// Find if there are at least one undirected edge
		boolean undirected = false;
		for ( CyEdge cyEdge : net.getConnectingEdgeList( node, neighbor, CyEdge.Type.ANY ) )
			if ( ! net.getRow(cyEdge).get(MlnBuilder.DIRECTION, Boolean.class) )
				undirected = true;
		// Return the list of edges if this is the case
		if ( undirected ) return net.getConnectingEdgeList( node, neighbor, CyEdge.Type.ANY );
		else return new ArrayList<CyEdge>();
	}
	
	/*
	 * Get all the multi-edges going to opposite directions.
	 * @param network of the nodes
	 * @param first node of the node pair
	 * @param second node of the node pair
	 * @return the list of edges
	 */
	public static List<CyEdge> getOppositeDirectedMultiEdges( CyNetwork net, CyNode node, CyNode neighbor ) {
		// Find if the edges are going in opposite directions
		boolean opposite = false;
		CyNode source = null;
		for ( CyEdge cyEdge : net.getConnectingEdgeList( node, neighbor, CyEdge.Type.ANY ) ) {
			boolean isDirected = net.getRow(cyEdge).get(MlnBuilder.DIRECTION, Boolean.class);
			// if there are no edges with opposite directions, all the edges will have the same source node
			if ( source == null && isDirected ) source = cyEdge.getSource();
			// the edge has to be directed and the source node has to be different 
			if ( isDirected && source != cyEdge.getSource() )
				opposite = true;
		}
		// Return the list of edges if this is the case
		if ( opposite ) return net.getConnectingEdgeList( node, neighbor, CyEdge.Type.ANY );
		else return new ArrayList<CyEdge>();
	}
	
	/*
	 * Get all multi-edges, even those with opposite directions of a node pair.
	 * @param network of the nodes
	 * @param first node of the node pair
	 * @param second node of the node pair
	 * @return the list of edges
	 */
	public static List<CyEdge> getAllMultiEdges( CyNetwork net, CyNode node, CyNode neighbor ){
		return net.getConnectingEdgeList( node, neighbor, CyEdge.Type.ANY );
	}
	
	/*
	 * Get the outgoing directed edges of a node pair.
	 * @param network of the nodes
	 * @param first node of the node pair
	 * @param second node of the node pair
	 * @return the list of edges
	 */
	public static List<CyEdge> getOutgoingDirectedMultiEdges( CyNetwork net, CyNode node, CyNode neighbor ) {
		List<CyEdge> multiEdges = new ArrayList<CyEdge>();
		// Get the edges connecting the node pair
		for ( CyEdge cyEdge : net.getConnectingEdgeList( node, neighbor, CyEdge.Type.ANY ) )
			// Outgoing edges are directed and their source node is the node itself
			if ( net.getRow(cyEdge).get(MlnBuilder.DIRECTION, Boolean.class) && cyEdge.getSource() == node )
				multiEdges.add(cyEdge);
		return multiEdges;
	}
	
	/*
	 * Get the incoming directed edges of a node pair.
	 * @param network of the nodes
	 * @param first node of the node pair
	 * @param second node of the node pair
	 * @return the list of edges
	 */
	public static List<CyEdge> getIncomingDirectedMultiEdges( CyNetwork net, CyNode node, CyNode neighbor ) {
		List<CyEdge> multiEdges = new ArrayList<CyEdge>();
		// Get the edges connecting the node pair
		for ( CyEdge cyEdge : net.getConnectingEdgeList( node, neighbor, CyEdge.Type.ANY ) )
			// Incoming edges are directed and their source node is the neighbor
			if ( net.getRow(cyEdge).get(MlnBuilder.DIRECTION, Boolean.class) && cyEdge.getSource() == neighbor )
				multiEdges.add(cyEdge);
		return multiEdges;
	}
}

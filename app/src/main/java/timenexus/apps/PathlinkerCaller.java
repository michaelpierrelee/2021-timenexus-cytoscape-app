package timenexus.apps;

import java.awt.Component;
import java.awt.Dimension;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.NumberFormatter;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.work.TaskMonitor;
import org.json.JSONArray;
import org.json.JSONObject;

import timenexus.extraction.ExtractionMethod;
import timenexus.extraction.ExtractionMethod.MlnExtractionException;
import timenexus.temporalnetwork.MlnBuilder;
import timenexus.utils.HttpRequestToAPI;

/*
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class PathlinkerCaller implements AppCaller {
	
	//		Parameters of the app
	SpinnerModel numberK;
	JFormattedTextField edgePenaltyField;
	JComboBox<String> weightType;
	JComboBox<String> weightColName;
	JCheckBox checkNetworkIsDirected; // this is the opposite of pathlinker interface
	JCheckBox checkInPath;
	JCheckBox checkTiedPaths;
	JFormattedTextField cyrestPortField;
	HashMap<Integer, JComboBox<String>> queryColNames;
	boolean skipSubnetworkGeneration = true;
	
	// if a directed network has undirected edges and reciprocally
	boolean networkHasWrongDirections = false;
	// if the network contains multi-edges which are forbidden
	boolean networkHasMultiEdges = false;

	/*_______________________________________
	 * 
	 *			MANAGE PARAMETERS
	 *_______________________________________
	 */
	
	/*
	 * Set form elements of the parameters of the app
	 */
	public PathlinkerCaller( CySubNetwork flattenedNet, List<Integer> layers ) {
		// k
		numberK = new SpinnerNumberModel( 50, 1, null, 1 );
		// edge penalty
		NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(15);
        nf.setMinimumFractionDigits(0);
        NumberFormatter formatter = new NumberFormatter(nf);
        formatter.setMinimum(0.d);
		edgePenaltyField = new JFormattedTextField(formatter);
		edgePenaltyField.setValue( 1.0d );
		// edge weight type
		weightType = new JComboBox<String>();
		weightType.addItem( "PROBABILITIES" );
		weightType.addItem( "ADDITIVE" );
		weightType.addItem( "UNWEIGHTED" );
		// name of edge weight column
		weightColName = new JComboBox<String>();
		weightColName.addItem("Weight");
		weightColName.setEnabled(false);
		// network is directed
		checkNetworkIsDirected = new JCheckBox();
		checkNetworkIsDirected.setSelected(false);
		// sources and targets in paths
		checkInPath = new JCheckBox();
		checkInPath.setSelected(true);
		// allowSourcesTargetsInPaths tied paths
		checkTiedPaths = new JCheckBox();
		//checkTiedPaths.setSelected(true);
		// port of the cyRest
		NumberFormat nfi = NumberFormat.getIntegerInstance();
		nfi.setGroupingUsed(false);
        NumberFormatter formatterNfi = new NumberFormatter(nfi);
        formatterNfi.setMinimum(0);
		cyrestPortField = new JFormattedTextField(formatterNfi);
		cyrestPortField.setValue( 1234 );
		// columns containing the query nodes for each layer
		queryColNames = new HashMap<Integer, JComboBox<String>>();
		addBooleanQueryColNames( flattenedNet, queryColNames, layers );
	}
	
	/*
	 * Add form elements setting pathlinker parameters to a java component.
	 */
	@Override
	public void addParametersToPanel( JComponent comp, List<Integer> layers ) {
		//		Create boxes
		Box k = Box.createHorizontalBox();
		Box edgePenalty = Box.createHorizontalBox();
		Box edgeWeightType = Box.createHorizontalBox();
		Box edgeWeightCol = Box.createHorizontalBox();
		Box treatNetworkAsUndirected = Box.createHorizontalBox();
		Box allowSourcesTargetsInPaths = Box.createHorizontalBox();
		Box includeTiedPaths = Box.createHorizontalBox();
		Box cyrestPort = Box.createHorizontalBox();
		Box selectQueryCols = Box.createVerticalBox();
			
		
		//		Add labels
		k.add( new JLabel("k (# of paths): ") );
		edgePenalty.add( new JLabel("Edge penalty: ") );
		edgeWeightType.add( new JLabel("Edge weights: ") );
		edgeWeightCol.add( new JLabel("Edge weight column: ") );
		treatNetworkAsUndirected.add( new JLabel("Network is directed: ") );
		allowSourcesTargetsInPaths.add( new JLabel("Allow sources and targets in paths: ") );
		includeTiedPaths.add( new JLabel("Include tied paths: ") );
		cyrestPort.add( new JLabel("cyRest Port: ") );
		selectQueryCols.setBorder( BorderFactory.createTitledBorder( "Select columns with query nodes:" ) );
		
		//		Add components
		// k
		k.add( new JSpinner( numberK ) );
		k.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( k );
		// edge penalty
		edgePenalty.add( edgePenaltyField );
		edgePenalty.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( edgePenalty );
		// edge weight type
		edgeWeightType.add( weightType );
		edgeWeightType.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( edgeWeightType );
		// name of edge weight column
		edgeWeightCol.add(weightColName);
		edgeWeightCol.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( edgeWeightCol );
		// network is directed
		treatNetworkAsUndirected.add( checkNetworkIsDirected );
		treatNetworkAsUndirected.setAlignmentX( Component.LEFT_ALIGNMENT );
		// sources and targets in paths
		allowSourcesTargetsInPaths.add( checkInPath );
		allowSourcesTargetsInPaths.setAlignmentX( Component.LEFT_ALIGNMENT );
		// allowSourcesTargetsInPaths tied paths
		includeTiedPaths.add( checkTiedPaths );
		includeTiedPaths.setAlignmentX( Component.LEFT_ALIGNMENT );
		// port of the cyRest
		cyrestPort.add( cyrestPortField );
		cyrestPort.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( cyrestPort );
		// columns containing the query nodes for each layer
		for (int i = 0; i < layers.size(); i++) {
			int layerID = layers.get(i);
			JComboBox<String> jComboBox = queryColNames.get(layerID);
				jComboBox.setMaximumSize( new Dimension( 150, jComboBox.getPreferredSize().height ) );
			Box row = Box.createHorizontalBox();
				row.add( Box.createHorizontalGlue() );	
				row.add( new JLabel( "Layer " + layerID + ": " ) );
				row.add( jComboBox );
				row.add( Box.createHorizontalGlue() );
				row.setAlignmentX( Component.LEFT_ALIGNMENT );
			selectQueryCols.add( row );
		}
		
		//		Add boxes to the component
		comp.add(k);
		comp.add(edgePenalty);
		comp.add(edgeWeightType);
		comp.add(edgeWeightCol);
		comp.add(treatNetworkAsUndirected);
		comp.add(allowSourcesTargetsInPaths);
		comp.add(includeTiedPaths);
		comp.add(cyrestPort);
		comp.add(selectQueryCols);
	}
	
	/*
	 * Set maximum height to standard values.
	 */
	private static void setMaxHeight( Component comp ) {
		comp.setMaximumSize( new Dimension( comp.getMaximumSize().width, comp.getPreferredSize().height ) );
	}
	
	/*
	 * Get class variable of column names containing query nodes.
	 */
	@Override
	public HashMap<Integer, JComboBox<String>> getQueryColNames(){
		return queryColNames;
	}
	
	/*_______________________________________
	 * 
	 *			CHECK NETWORK
	 *_______________________________________
	 */
	
	/*
	 * Check if the multi-layer network can be processed by the app.
	 * @param the flattened network
	 * @return the message if the criteria are not met
	 */
	@Override
	public String checkNetwork(CyNetwork flattenedNet) {
		String message = null;
		boolean isNetworkDirected = checkNetworkIsDirected.isSelected();
		//		Check edge direction
		for ( CyEdge cyEdge : flattenedNet.getEdgeList() ) {
			Boolean isEdgeDirected = flattenedNet.getRow(cyEdge).get(MlnBuilder.DIRECTION, Boolean.class);
			// If the network is directed, then all the edges should be directed
			if ( isNetworkDirected && ( isEdgeDirected == null || ! isEdgeDirected ) ) {
				networkHasWrongDirections = true;
				message = "-The multi-layer network has undirected edges, while they are expected to be directed.";
				break;
			}
			// If the network is undirected, then all the edges should be undirected
			else if ( ! isNetworkDirected && isEdgeDirected != null && isEdgeDirected ) {
				networkHasWrongDirections = true;
				message = "-The multi-layer network has directed edges, while they are expected to be undirected.";
				break;
			}
		}
		//		Check multi-edges
		String multiEdgesMessage = "- The multi-layer network has multi-edges, "
				+ "but PathLinker cannot process them (except for opposite edges within a directed network).";
		// If the network is undirected, check for any multi-edges regardless their direction
		if ( ! isNetworkDirected && EdgeManagement.isNetworkHasMultiEdges(flattenedNet) ) {
			networkHasMultiEdges = true;
			if ( message != null ) message += "\n" + multiEdgesMessage;
			else message = multiEdgesMessage;
		}
		// If the network is directed
		else if ( isNetworkDirected ) {
			List<List<CyNode>> directedEdges = new ArrayList<List<CyNode>>();
			for ( CyEdge cyEdge : flattenedNet.getEdgeList() ) {
				if ( flattenedNet.getRow(cyEdge).get(MlnBuilder.DIRECTION, Boolean.class) ) {
					List<CyNode> edge = List.of( cyEdge.getSource(), cyEdge.getTarget() ); 
					if ( directedEdges.contains( edge ) ) {
						networkHasMultiEdges = true;
						if ( message != null ) message += "\n" + multiEdgesMessage;
						else message = multiEdgesMessage;
						break;
					} else
						directedEdges.add( edge );
				}
			}
		}
		//		Return
		return message;
	}
	
	/*_______________________________________
	 * 
	 *			CALL THE APP
	 *_______________________________________
	 */
	
	
	/*
	 * Extract subnetwork using PathLinker.
	 * @param network to extract
	 * @param query-source node
	 * @param query-target node
	 * @param task monitor of the extraction method
	 * @param if true, the task is cancelled
	 * @return set of nodes extracted from the network
	 */
	@Override
	public ExtractedNetwork call( CyNetwork networkToExtract, Map<String, String> querySources, Map<String, String> queryTargets,
			TaskMonitor taskMonitor, ExtractionMethod method )
			throws MlnAppCallerException, MlnExtractionException {
		
		//		Check if query-node names contain spaces
		for (String node : querySources.keySet()) {
			if ( node.contains(" ") ) throw new MlnAppCallerException( "PathLinker does not allow spaces within query-node names.",
					"PathLinker extraction failed", JOptionPane.ERROR_MESSAGE );
		}
		for (String node : queryTargets.keySet()) {
			if ( node.contains(" ") ) throw new MlnAppCallerException( "PathLinker does not allow spaces within query-node names.",
					"PathLinker extraction failed", JOptionPane.ERROR_MESSAGE );
		}
		
		//		Correct the multi-layer network
		boolean isNetworkDirected = checkNetworkIsDirected.isSelected();
		// Have all the edges as directed
		if ( networkHasWrongDirections && isNetworkDirected ) {
			taskMonitor.setStatusMessage("Converting network as directed...");
			EdgeManagement.setNetworkAsDirected( networkToExtract, method );
		// Have all the edges as undirected
		} else if ( networkHasWrongDirections && ! isNetworkDirected ) {
			taskMonitor.setStatusMessage("Converting network as undirected...");
			EdgeManagement.setNetworkAsUndirected( networkToExtract, method );
		// Aggregate the edges for a directed network
		} if ( networkHasMultiEdges && isNetworkDirected ) {
			taskMonitor.setStatusMessage("Aggregating undirected multi-edges...");
			EdgeManagement.aggregateUndirectedMultiEdges( networkToExtract, method );
		// Aggregate the edges for an undirected network
		} else if ( networkHasMultiEdges && ! isNetworkDirected ) {
			taskMonitor.setStatusMessage("Aggregating directed multi-edges...");
			EdgeManagement.aggregateIdenticallyDirectedMultiEdges( networkToExtract, method );
		}
		
		//		Prepare data
		// Build the URL
		Long suid = networkToExtract.getRow( networkToExtract ).get( CyNetwork.SUID, Long.class );
		String url = "http://localhost:1234/pathlinker/v1/" + suid + "/run";
		// Parse sources and targets
		String sources = String.join(" ", querySources.keySet());
		String targets = String.join(" ", queryTargets.keySet());
		// Build the data to send
		JSONObject json = new JSONObject( getParameters( sources, targets ) );
		
		//		Send data
		try {
			method.checkCancelling();
			taskMonitor.setStatusMessage("Waiting for the PathLinker app to process data...");
			// Send
			HttpResponse<String> response = HttpRequestToAPI.sendJSON(url, json);
			// Test if connection was found
			if ( response.statusCode() == 404 )
				throw new MlnAppCallerException( "404 error. PathLinker CyRest interface was not found.\n"
						+ "Please check that PathLinker is installed and running with the menu Help > Automation > CyRest API.",
						"PathLinker not found", JOptionPane.ERROR_MESSAGE );
			// Retrieve response
			JSONObject retrievedJson = new JSONObject( response.body() );
			//Print.out(response.body());
			// Manage errors
			if ( response.statusCode() != 200 ) {
				JSONObject error = retrievedJson.getJSONArray("errors").getJSONObject(0);
				throw new MlnAppCallerException( "HTTP status code: " + response.statusCode() + "\n"
					+ error.get("message") + "\n" + error.get("type") + "\n" + error.get("link") + "\n\n"
					+ "NB: PathLinker returns an error 500 when sources and targets belong to independent components of the network.",
						"Connection error to PathLinker", JOptionPane.ERROR_MESSAGE );
			} else if ( retrievedJson == null || retrievedJson.isEmpty() || ! retrievedJson.keySet().contains("paths") )
				throw new MlnAppCallerException( "No data was received from Pathlinker, but connection to its CyRest interface was OK.",
						"No data from PathLinker", JOptionPane.ERROR_MESSAGE );
			// Return
			JSONArray jsonData = retrievedJson.getJSONArray("paths");
			return parsePathlinkerData( jsonData );
		} catch (URISyntaxException | IOException | InterruptedException err) {
			throw new MlnAppCallerException( "Connection to the PathLinker CyRest interface failed",
					"Connection error to PathLinker", JOptionPane.ERROR_MESSAGE, err );
		}
	}
	
	
	/*
	 * Get parameters from the form elements.
	 * @return map of parameters.
	 */
	private Map<String, Object> getParameters( String sources, String targets ) throws MlnAppCallerException {
		//		Check whether the parameters are defined
		List<String> nullParams = new ArrayList<String>();
		if ( sources == null || sources.equals("") ) nullParams.add("Query-source nodes");
		if ( targets == null || targets.equals("") ) nullParams.add("Query-target nodes");
		if ( numberK == null  ) nullParams.add("k");
		if ( edgePenaltyField == null  ) nullParams.add("edgePenalty");
		if ( weightType == null  ) nullParams.add("edgeWeightType");
		if ( weightColName == null  ) nullParams.add("edgeWeightColumnName");
		if ( checkNetworkIsDirected == null  ) nullParams.add("treatNetworkAsUndirected");
		if ( checkInPath == null  ) nullParams.add("allowSourcesTargetsInPaths");
		if ( checkTiedPaths == null  ) nullParams.add("includeTiedPaths");
		if ( cyrestPortField == null  ) nullParams.add("cyRestPort");
			
		if ( ! nullParams.isEmpty() )
			throw new MlnAppCallerException( "Connection to PathLinker was aborted as some parameters are null:\n"
					+ Arrays.toString( nullParams.toArray() ),
					"Connection to PathLinker aborted", JOptionPane.ERROR_MESSAGE );
			
		//		Return parameters
		Map<String, Object> listParameters = new HashMap<String, Object>();
		listParameters.put( "sources", sources );
		listParameters.put( "targets", targets );
		listParameters.put( "k", numberK.getValue() );
		listParameters.put( "edgePenalty", edgePenaltyField.getValue() );
		listParameters.put( "edgeWeightType", weightType.getSelectedItem() );
		listParameters.put( "edgeWeightColumnName", weightColName.getSelectedItem() );
		listParameters.put( "treatNetworkAsUndirected", ! checkNetworkIsDirected.isSelected() ); // this is the opposite of pathlinker interface
		listParameters.put( "allowSourcesTargetsInPaths", checkInPath.isSelected() );
		listParameters.put( "includeTiedPaths", checkTiedPaths.isSelected() );
		listParameters.put( "skipSubnetworkGeneration", skipSubnetworkGeneration );
		
		return listParameters;
	}
	
	
	/*
	 * Parse the PathLinker response into an ExtractedNetwork object.
	 */
	private ExtractedNetwork parsePathlinkerData( JSONArray jsonData ) {
		// Get data from the extracted subnetwork
		Set<String> nodes = new HashSet<String>();
		Map<List<String>, List<Double>> scores = new HashMap<List<String>, List<Double>>();
		Map<List<String>, List<Integer>> ranks = new HashMap<List<String>, List<Integer>>();
		for (int i = 0; i < jsonData.length(); i++) {
			// Get data for each path
			JSONObject path = jsonData.getJSONObject(i);
			JSONArray nodeList = path.getJSONArray("nodeList"); // 3 keys: score, rank, nodeList
			Double score = path.getDouble("score");
			Integer rank = path.getInt("rank");
			// Add data to the lists
			for (int j = 0; j < nodeList.length() - 1; j++) {
				String node1 = nodeList.getString(j);
				String node2 = nodeList.getString(j+1);
				List<String> edge1 = Arrays.asList( node1, node2 );
				List<String> edge2 = Arrays.asList( node2, node1 );
				// Create lists of scores and ranks for each node
				if( ! scores.containsKey(edge1) ) {
					scores.put( edge1 , new ArrayList<Double>() );
					ranks.put( edge1 , new ArrayList<Integer>() );
				}
				if( ! checkNetworkIsDirected.isSelected() && ! scores.containsKey(edge2) ) {
					scores.put( edge2 , new ArrayList<Double>() );
					ranks.put( edge2 , new ArrayList<Integer>() );
				}
				// Add scores and ranks to the lists
				if (j == 0) nodes.add(node1);
				nodes.add(node2);
				scores.get(edge1).add(score);
				ranks.get(edge1).add(rank);
				if( ! checkNetworkIsDirected.isSelected() ) {
					scores.get(edge2).add(score);
					ranks.get(edge2).add(rank);
				}
			}
		}
		// Create storage object of the subnetwork
		ExtractedNetwork net = new ExtractedNetwork(
				new ArrayList<String>(nodes),
				new ArrayList<List<String>>(scores.keySet()) );
		net.addEdgeListAttributes( "PathLinker_score", new ArrayList<List<Double>>( scores.values() ), Double.class );
		net.addEdgeListAttributes( "PathLinker_rank", new ArrayList<List<Integer>>( ranks.values() ), Integer.class );
		return net;
	}
	
	@Override
	public String toString() { return "PathLinker app"; }

	

}


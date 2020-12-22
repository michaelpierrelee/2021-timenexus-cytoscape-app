package timenexus.apps;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.xml.bind.JAXBException;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.work.TaskMonitor;

import timenexus.extraction.ExtractionMethod;
import timenexus.extraction.ExtractionMethod.MlnExtractionException;
import timenexus.temporalnetwork.MlnBuilder;
import timenexus.utils.HttpRequestToAPI;
import timenexus.utils.Print;

/*
 * The user should note that AnatApp doesn't enable two edges for a same node pair.
 * It involves that only directed 
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class AnatCaller implements AppCaller {

	// Parameters of the app
	ButtonGroup algoRadioGroup;
	ButtonGroup subAlgoRadioGroup;
	SpinnerModel alpha; // balance between global and local criteria
	JCheckBox ignoreWeight;
	JComboBox<String> weightColName;
	SpinnerModel edgePenalty; //  balance between the size (number of edges) of the subnetwork and its overall confidence (sum of edge/node weights)
	SpinnerModel margin; // % of sub-optimal solutions added to the final results
	JCheckBox enableNodePenalty;
	SpinnerModel curvature; // penalty of highly connected proteins
	SpinnerModel dominance; //  relative importance of node weights compared to edge weights
	JCheckBox completion; // run iPoint algorithm to completion
	//JCheckBox predictTF; // predict transcription factors for a protein-DNA network
	JCheckBox propagate; // predict anchors using network propagation
	//JCheckBox terminalsToAnchors; // search from terminals to anchors, instead of the opposite
	SpinnerModel degree; // desired distance around the input nodes for the local search
	SpinnerModel granularity; //  control the number of connected components in the inferred network
	HashMap<Integer, JComboBox<String>> queryBooleanColNames; // boolean columns from the node table
	HashMap<Integer, JComboBox<String>> queryStringColNames; // string columns from the node table (for shortest paths)
	
	// if the node-penalty parameters should be displayed
	boolean displayNodePenaltyParam = false;
	// number of seconds before the algorithm stops to wait for an answer from the anat server
	int timeOutInSeconds = 999999999;
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
	public AnatCaller( CySubNetwork flattenedNet, List<Integer> layers ) {
		// algorithm type
		JRadioButton anchored = new JRadioButton( "Anchored" );
			anchored.setActionCommand( anchored.getText() );
			anchored.setToolTipText("Optimize local (reliable pathway from the anchor to the terminal nodes) "
					+ "and global (parsimonious subnetwork) features of the subnetwork. Sources and targets should not overlap.");
			anchored.setSelected(true);
		JRadioButton general = new JRadioButton( "General" );
			general.setActionCommand( general.getText() );
			general.setToolTipText("Solve a Steiner tree problem. It ignores edge directions and node weights.");
		JRadioButton shortest = new JRadioButton( "Shortest" );
			shortest.setActionCommand( shortest.getText() );
			shortest.setToolTipText("Solve a shortest path problem.");
		JRadioButton local = new JRadioButton( "Local" );
			local.setActionCommand( local.getText() );
			local.setToolTipText("Find neighbours that are at most 'd' links (the 'degree' parameter) away the query nodes.");
		algoRadioGroup = new ButtonGroup();
			algoRadioGroup.add(anchored);
			algoRadioGroup.add(general);
			algoRadioGroup.add(shortest);
			algoRadioGroup.add(local);
		// sub algorithm
		JRadioButton approximate = new JRadioButton( "Approximate" );
			approximate.setActionCommand( approximate.getText() );
			approximate.setToolTipText("Approximation of the anchored-network algorithm (quick).");
			approximate.setSelected(true);
		JRadioButton exact = new JRadioButton( "Exact" );
			exact.setActionCommand( exact.getText() );
			exact.setToolTipText("Find the exact solution of the anchored-network algorithm with iPoint (slow).");
		subAlgoRadioGroup = new ButtonGroup();
			subAlgoRadioGroup.add(approximate);
			subAlgoRadioGroup.add(exact);
		// alpha
		alpha = new SpinnerNumberModel( 0.25, 0, 0.50, 0.01 );
		// unweighted network
		ignoreWeight = new JCheckBox();
		// node and edge weight column name
		weightColName = new JComboBox<String>();
		weightColName.addItem("Weight");
		weightColName.setEnabled(false);
		// edge penalty
		edgePenalty = new SpinnerNumberModel( 25, 0, 100, 1 );
		// margin
		margin = new SpinnerNumberModel( 0, 0, 25, 1 );
		// enable node penalty
		enableNodePenalty = new JCheckBox();
		// curvature (for node penalty)
		curvature = new SpinnerNumberModel( 3, 0, null, 1 );
		// dominance (for node penalty)
		dominance = new SpinnerNumberModel( 1, 0, null, 1 );
		// propagate
		propagate = new JCheckBox();
		// completion
		completion = new JCheckBox();
		// granularity
		granularity = new SpinnerNumberModel( 0, 0, 100, 1 );
		// degree
		degree = new SpinnerNumberModel( 1, 1, 10, 1 );
		// columns containing the query nodes for each layer
		queryBooleanColNames = new HashMap<Integer, JComboBox<String>>();
		addBooleanQueryColNames( flattenedNet, queryBooleanColNames, layers );
		queryStringColNames = new HashMap<Integer, JComboBox<String>>();
		addStringQueryColNames( flattenedNet, queryStringColNames, layers );
	}
	
	@Override
	public void addParametersToPanel(JComponent comp, List<Integer> layers) {
		//		Create boxes
		Box algoBox = Box.createHorizontalBox();
		Box subAlgoBox = Box.createHorizontalBox();
		Box alphaBox = Box.createHorizontalBox();
		Box ignoreWeightBox = Box.createHorizontalBox();
		Box weightColBox = Box.createHorizontalBox();
		Box edgePenaltyBox = Box.createHorizontalBox();
		Box marginBox = Box.createHorizontalBox();
		Box enableNodePenaltyBox = Box.createHorizontalBox();
		Box curvatureBox = Box.createHorizontalBox();
		Box dominanceBox = Box.createHorizontalBox();
		Box completionBox = Box.createHorizontalBox();
		Box propagateBox = Box.createHorizontalBox();
		Box granularityBox = Box.createHorizontalBox();
		Box degreeBox = Box.createHorizontalBox();
		Box selectBooleanQueryCols = Box.createVerticalBox();
		Box selectStringQueryCols = Box.createVerticalBox();
		
		//		Add tool tips
		alphaBox.setToolTipText("Set the balance between local and global criteria.");
		edgePenaltyBox.setToolTipText("Balance between the size " + 
				"(number of edges) of the subnetwork and its overall confidence " + 
				"(sum of edge/node weights.)");
		marginBox.setToolTipText("% of sub-optimal solutions added to the final results.");
		enableNodePenaltyBox.setToolTipText(" Assigning a penalty factor assigned to each node, " + 
				"defined inverse-proportionally to its degree. Is is controlled by the curvature and dominance parameters.");
		curvatureBox.setToolTipText("Control the penalty of highly connected proteins compared to proteins with a lower degree.");
		dominanceBox.setToolTipText("Adjust the relative importance of node weights compared to edge weights.");
		completionBox.setToolTipText("Run the exact algorithm until completion.");
		propagateBox.setToolTipText("Predict anchors using network propagation.");
		granularityBox.setToolTipText("Control the number of connected components in the subnetwork.");
		degreeBox.setToolTipText("Return all the proteins that are at most d links away from at least one " + 
				"protein in the input set.");

		//		Add labels
		algoBox.setBorder( BorderFactory.createTitledBorder( "Algorithm type" ) );
		subAlgoBox.setBorder( BorderFactory.createTitledBorder( "Sub-algorithm" ) );
		alphaBox.add( new JLabel("Global-local balance: ") );
		ignoreWeightBox.add( new JLabel("Unweighted network: ") );
		weightColBox.add( new JLabel("Weight columns: ") );
		marginBox.add( new JLabel("Margin (%): ") );
		edgePenaltyBox.add( new JLabel("Edge penalty (%): ") );
		enableNodePenaltyBox.add( new JLabel("Enable node penalty: ") );
		curvatureBox.add( new JLabel("Node-penalty curvature: ") );
		dominanceBox.add( new JLabel("Node-penalty dominance: ") );
		completionBox.add( new JLabel("Run to completion: ") );
		propagateBox.add( new JLabel("Predict anchors: ") );
		granularityBox.add( new JLabel("Granularity: ") );
		degreeBox.add( new JLabel("Degree: ") );
		selectBooleanQueryCols.setBorder( BorderFactory.createTitledBorder( "Select columns with query nodes:" ) );
		selectStringQueryCols.setBorder( BorderFactory.createTitledBorder( "Select columns with query nodes:" ) );
		
		//		Add components
		// algorithm type
		Enumeration<AbstractButton> enumAlgo = algoRadioGroup.getElements();
		algoBox.add( Box.createHorizontalGlue() );
		while (enumAlgo.hasMoreElements()) {
			algoBox.add( enumAlgo.nextElement() );
			algoBox.add( Box.createHorizontalGlue() );
		}
		algoBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( algoBox );
		// sub algorithm
		Enumeration<AbstractButton> enumSubAlgo = subAlgoRadioGroup.getElements();
		subAlgoBox.add( Box.createHorizontalGlue() );
		while (enumSubAlgo.hasMoreElements()) {
			subAlgoBox.add( enumSubAlgo.nextElement() );
			subAlgoBox.add( Box.createHorizontalGlue() );
		}
		subAlgoBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( subAlgoBox );
		// alpha
		alphaBox.add( new JSpinner(alpha) );
		alphaBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( alphaBox );
		// ignore weight
		ignoreWeightBox.add( ignoreWeight );
		ignoreWeightBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( ignoreWeightBox );
		// name of the weight columns
		weightColBox.add( weightColName );
		weightColBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( weightColBox );
		// margin
		marginBox.add( new JSpinner(margin) );
		marginBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( marginBox );
		// edge penalty
		edgePenaltyBox.add( new JSpinner(edgePenalty) );
		edgePenaltyBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( edgePenaltyBox );
		// enable node penalty
		enableNodePenaltyBox.add( enableNodePenalty );
		enableNodePenaltyBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( enableNodePenaltyBox );
		// curvature
		curvatureBox.add( new JSpinner(curvature) );
		curvatureBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( curvatureBox );
		// dominance
		dominanceBox.add( new JSpinner(dominance) );
		dominanceBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( dominanceBox );
		// completion
		completionBox.add( completion );
		completionBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( completionBox );
		// propagate
		propagateBox.add( propagate );
		propagateBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( propagateBox );
		// granularity
		granularityBox.add( new JSpinner(granularity) );
		granularityBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( granularityBox );
		// degree
		degreeBox.add( new JSpinner(degree) );
		degreeBox.setAlignmentX( Component.LEFT_ALIGNMENT );
		setMaxHeight( degreeBox );
		// columns containing the query nodes for each layer
		for (int i = 0; i < layers.size(); i++) {
			int layerID = layers.get(i);
			selectBooleanQueryCols.add( createQueryComboBox( queryBooleanColNames, layerID ) );
			selectStringQueryCols.add( createQueryComboBox( queryStringColNames, layerID ) );
			
		}
		
		//		Add boxes to the component
		comp.add(algoBox);
		comp.add(subAlgoBox);
		comp.add(alphaBox);
		comp.add(ignoreWeightBox);
		comp.add(weightColBox);
		comp.add(marginBox);
		comp.add(edgePenaltyBox);
		comp.add(enableNodePenaltyBox);
		comp.add(curvatureBox);
		comp.add(dominanceBox);
		comp.add(granularityBox);
		comp.add(degreeBox);
		comp.add(propagateBox);
		comp.add(completionBox);
		comp.add(selectBooleanQueryCols);
		comp.add(selectStringQueryCols);
		
		//		Add listeners
		// Update algorithm parameters
		Box[] generalParamBoxes = {
				marginBox,
				edgePenaltyBox,
				granularityBox,
				selectBooleanQueryCols
		};
		Box[] anchoredParamBoxes = {
				subAlgoBox,
				alphaBox,
				marginBox,
				edgePenaltyBox,
				enableNodePenaltyBox,
				curvatureBox,
				dominanceBox,
				completionBox,
				propagateBox,
				selectBooleanQueryCols
		};
		Box[] localParamBoxes = {
				degreeBox,
				selectBooleanQueryCols
		};
		Box[] shortestParamBoxes = {
				marginBox,
				edgePenaltyBox,
				enableNodePenaltyBox,
				curvatureBox,
				dominanceBox,
				selectStringQueryCols
		};
		enumAlgo = algoRadioGroup.getElements();
		while (enumAlgo.hasMoreElements()) {
			AbstractButton radio = enumAlgo.nextElement();
			radio.addActionListener( new AlgorithmSelectionListener(
					radio,
					generalParamBoxes, anchoredParamBoxes,
					localParamBoxes, shortestParamBoxes,
					curvatureBox, dominanceBox
					) );
		}
		// Set visible node-penalty parameters
		enableNodePenalty.addActionListener( new NodePenaltyListener(curvatureBox, dominanceBox) );
		
		//		Preselect anchored-network parameters
		for (Box box : localParamBoxes) box.setVisible(false);
		for (Box box : shortestParamBoxes) box.setVisible(false);
		for (Box box : generalParamBoxes) box.setVisible(false);
		for (Box box : anchoredParamBoxes) box.setVisible(true);
		curvatureBox.setVisible(false);
		dominanceBox.setVisible(false);
	}
	
	/*
	 * Set maximum height to standard values.
	 */
	private void setMaxHeight( Component comp ) {
		comp.setMaximumSize( new Dimension( comp.getMaximumSize().width, comp.getPreferredSize().height ) );
	}
	
	/*
	 * Create a combo box with a query column for a given layer. 
	 */
	private Box createQueryComboBox( HashMap<Integer, JComboBox<String>> queryColNames, int layerID ) {
		JComboBox<String> jComboBox = queryColNames.get(layerID);
		jComboBox.setMaximumSize( new Dimension( 150, jComboBox.getPreferredSize().height ) );
		Box row = Box.createHorizontalBox();
			row.add( Box.createHorizontalGlue() );	
			row.add( new JLabel( "Layer " + layerID + ": " ) );
			row.add( jComboBox );
			row.add( Box.createHorizontalGlue() );
			row.setAlignmentX( Component.LEFT_ALIGNMENT );
		return row;
	}

	/*
	 * Get class variable of column names containing query nodes.
	 */
	@Override
	public HashMap<Integer, JComboBox<String>> getQueryColNames() {
		String algo = algoRadioGroup.getSelection().getActionCommand();
		if ( algo.equals("Shortest") ) return queryStringColNames;
		else return queryBooleanColNames;
	}


	/*_______________________________________
	 * 
	 *			LISTENERS
	 *_______________________________________
	 */
	
	/*
	 * Update algorithm parameters when the algorithm is selected.
	 */
	private class AlgorithmSelectionListener implements ActionListener{

		AbstractButton radio;
		Box[] generalParamBoxes,
			anchoredParamBoxes,
			localParamBoxes,
			shortestParamBoxes;
		Box curvatureBox,
			dominanceBox;
		
		public AlgorithmSelectionListener(AbstractButton radio,
				Box[] generalParamBoxes, Box[] anchoredParamBoxes,
				Box[] localParamBoxes, Box[] shortestParamBoxes,
				Box curvatureBox, Box dominanceBox) {
			this.radio = radio;
			this.generalParamBoxes = generalParamBoxes;
			this.anchoredParamBoxes = anchoredParamBoxes;
			this.localParamBoxes = localParamBoxes;
			this.shortestParamBoxes = shortestParamBoxes;
			this.curvatureBox = curvatureBox;
			this.dominanceBox = dominanceBox;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			String algoName = radio.getActionCommand();
			// Reset display
			for (Box box : anchoredParamBoxes) box.setVisible(false);
			for (Box box : localParamBoxes) box.setVisible(false);
			for (Box box : shortestParamBoxes) box.setVisible(false);
			for (Box box : generalParamBoxes) box.setVisible(false);
			// Display the parameters of the selected algorithm
			if ( algoName.equals("General") )
				for (Box box : generalParamBoxes) box.setVisible(true);
			else if ( algoName.equals("Anchored") )
				for (Box box : anchoredParamBoxes) box.setVisible(true);
			else if ( algoName.equals("Local") )
				for (Box box : localParamBoxes) box.setVisible(true);
			else if ( algoName.equals("Shortest") )
				for (Box box : shortestParamBoxes) box.setVisible(true);
			// Set the display of node-penalty parameters
			if ( algoName.equals("Anchored") || algoName.equals("Shortest") ) {
				curvatureBox.setVisible(displayNodePenaltyParam);
				dominanceBox.setVisible(displayNodePenaltyParam);
			}
		}
		
	}
	
	/*
	 * Enable or disable the node-penalty parameters.
	 */
	private class NodePenaltyListener implements ActionListener{
		
		Box curvatureBox;
		Box dominanceBox;

		public NodePenaltyListener( Box curvatureBox, Box dominanceBox ) {
			this.curvatureBox = curvatureBox;
			this.dominanceBox = dominanceBox;
		}
		
		@Override
		public void actionPerformed(ActionEvent e) {
			displayNodePenaltyParam = ! displayNodePenaltyParam;
			curvatureBox.setVisible(displayNodePenaltyParam);
			dominanceBox.setVisible(displayNodePenaltyParam);
		}
		
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
		// Check for any multi-edges regardless their direction
		if ( EdgeManagement.isNetworkHasMultiEdges(flattenedNet) ) {
			networkHasMultiEdges = true;
			return "- The multi-layer network has multi-edges, but Anat cannot process them.";
		}
		else return null;
	}
	
	/*_______________________________________
	 * 
	 *			CALL THE APP
	 *_______________________________________
	 */
	
	/*
	 * Extract subnetwork using Anat Server.
	 * @param network to extract
	 * @param query-source node
	 * @param query-target node
	 * @param task monitor of the extraction method
	 * @param if true, the task is cancelled
	 * @return set of nodes extracted from the network
	 */
	@Override
	public ExtractedNetwork call( CyNetwork network, Map<String, String> querySources, Map<String, String> queryTargets,
			TaskMonitor taskMonitor, ExtractionMethod method )
			throws MlnAppCallerException, MlnExtractionException {
		//		Correct the multi-layer network
		if ( networkHasMultiEdges && ! method.isCancelled() ) {
			taskMonitor.setStatusMessage("Aggregating multi-edges...");
			EdgeManagement.aggregateMixedMultiEdges( network, method );
		}
		
		//		Get the algorithm which has to be used
		String algo = algoRadioGroup.getSelection().getActionCommand();
		
		//		Get elements from the network
		taskMonitor.setStatusMessage("Preparing data to send...");
		// Nodes
		List<String> nodes = new ArrayList<String>();
		List<Double> nodeWeights = new ArrayList<Double>();
		for ( CyNode node : network.getNodeList() ) {
			nodes.add( network.getRow(node).get(CyNetwork.NAME, String.class) );
			nodeWeights.add( network.getRow(node).get(MlnBuilder.WEIGHT, Double.class) );
		}
		// Edges
		List<List<String>> edges = new ArrayList<List<String>>();
		List<Double> edgeWeights = new ArrayList<Double>();
		List<Boolean> directions = new ArrayList<Boolean>();
		for ( CyEdge edge : network.getEdgeList() ) {
			List<String> e = new ArrayList<String>();
			e.add( network.getRow( edge.getSource() ).get( CyNetwork.NAME, String.class ) ); // add the source of the edge
			e.add( network.getRow( edge.getTarget() ).get( CyNetwork.NAME, String.class ) ); // add the target of the edge
			edges.add(e); // add the edge to the list
			edgeWeights.add( network.getRow(edge).get(MlnBuilder.WEIGHT, Double.class) ); // add the direction of the edge
			directions.add( network.getRow(edge).get(MlnBuilder.DIRECTION, Boolean.class) ); // add the weight of the edge
		}
		
		//		Check whether the queries are defined
		// Query source nodes need to be defined only for anchored networks
		List<String> nullParams = new ArrayList<String>();
		if ( querySources.isEmpty() ) nullParams.add("Query-source nodes");
		if ( queryTargets.isEmpty() && algo.equals("Anchored") ) nullParams.add("Query-target nodes");
		if ( ! nullParams.isEmpty() )
			throw new MlnAppCallerException( "Connection to Anat Server was aborted as some parameters are null:\n"
					+ Arrays.toString( nullParams.toArray() ),
					"Connection to Anat Server aborted", JOptionPane.ERROR_MESSAGE );
		
		//		Send the requests
		AnatSoap soap = null;
		// Create the query objects
		List<String> nodesSet = new ArrayList<String>(querySources.keySet());
			nodesSet.addAll( queryTargets.keySet() );
		Map<String, String> extremityPathNodes = new HashMap<String, String>(querySources);
			extremityPathNodes.putAll(queryTargets);
		// Create the requests
		try {
			if ( algo.equals("Anchored") )
				soap = getAnchoredSoap(nodes, edges, nodeWeights, edgeWeights, directions,
						new ArrayList<String>(querySources.keySet()),
						new ArrayList<String>(queryTargets.keySet()) );
			else if ( algo.equals("General") )
				soap = getGeneralSoap(nodes, edges, nodeWeights, edgeWeights, directions, nodesSet);
			else if ( algo.equals("Local") )
				soap = getLocalSoap(nodes, edges, nodeWeights, edgeWeights, directions, nodesSet);
			else if ( algo.equals("Shortest") )
				soap = getShortestSoap(nodes, edges, nodeWeights, edgeWeights, directions, extremityPathNodes);
		} catch (JAXBException e) {
			throw new MlnAppCallerException("Impossible to create a request from the parameters.",
					"Anat request error", JOptionPane.ERROR_MESSAGE, e);
		}
		// Send the requests
		try {
			method.checkCancelling();
			taskMonitor.setStatusMessage("Sending data to the Anat Server...");
			// Send the network
			HttpResponse<String> responseNetworkRequest = HttpRequestToAPI.sendSOAP(
					soap.getAnatServerURL(),
					soap.getXmlRequestForNetwork(),
					soap.getSoapAction()
					);
			int responseStatus = responseNetworkRequest.statusCode();
			String responseBody = responseNetworkRequest.body();
			//Print.out(soap.getXmlRequestForNetwork());
			/*Print.out(responseBody);*/
			// Get the results
			if ( responseStatus == 200 ) {
				boolean successParsing = false;
				int sleepTime = 1000, sleepTimeCumulated = 0, s = 0;
				//Print.out("---");
				//Print.out(soap.getID());
				while ( responseStatus == 200 && ! successParsing && s <= timeOutInSeconds ) {
					method.checkCancelling();
					taskMonitor.setStatusMessage("Waiting for the Anat Server to process the network ("+s+"s)...");
					HttpResponse<String> responseResultRequest = HttpRequestToAPI.sendSOAP(
							soap.getAnatServerURL(),
							soap.getXmlRequestForResults(),
							soap.getSoapActionResults()
							);
					responseStatus = responseResultRequest.statusCode();
					responseBody = responseResultRequest.body();
					//Print.out(responseBody);
					successParsing = soap.parseAnatResponse(responseBody);
					// sleep 1s if the request is good but there are no results
					if ( responseStatus == 200 && ! successParsing  ) Thread.sleep( sleepTime );
					sleepTimeCumulated += sleepTime;
					//Print.out(sleepTimeCumulated);
					sleepTime = sleepTimeCumulated < 60000 ? 1000 : 10000;
					s = sleepTimeCumulated / 1000;
				}
				// Throw error if unsuccessful parsing
				if ( ! successParsing && sleepTime >= timeOutInSeconds )
					throw new MlnAppCallerException( "Anat Server took too much time to send back a subnetwork. Extraction was aborted.",
							"Aborted extraction", JOptionPane.ERROR_MESSAGE );
				else if ( ! successParsing )
					throw new MlnAppCallerException( "Anat Server response could not be parsed into a subnetwork. Extraction was aborted.",
							"Aborted extraction", JOptionPane.ERROR_MESSAGE );
				// Display warnings and errors from the AnatServer
				if ( successParsing && soap.getWarnings() != null && ! soap.getWarnings().isEmpty() )
					Print.messageDialog("Warnings from Anat Server",
							String.join("\n", soap.getWarnings()), JOptionPane.WARNING_MESSAGE);
				if ( successParsing && soap.getErrors() != null && ! soap.getErrors().isEmpty() )
					Print.messageDialog("Errors from Anat Server",
							String.join("\n", soap.getErrors()), JOptionPane.ERROR_MESSAGE);
			}
			// Connection not found
			if ( responseStatus == 404 )
				throw new MlnAppCallerException( "404 error. Connection could not be established with the Anat Server.",
						"Anat Server not found", JOptionPane.ERROR_MESSAGE );
			// Other errors
			else if ( responseStatus != 200 ) {
				String[] faultString = responseBody.split("faultstring");
				throw new MlnAppCallerException( "HTTP status code: " + responseStatus + "\n"
						+ ( faultString.length > 2
								? Arrays.toString( Arrays.copyOfRange(faultString, 1, faultString.length - 1) )
								: responseBody ),
						"Error from Anat Server", JOptionPane.ERROR_MESSAGE );
			}

		} catch (JAXBException | URISyntaxException | IOException | InterruptedException err) {
			Print.error(err);
		}
		// Return the network
		return parseAnatData( soap );
	}
	
	/*
	 * Create an AnatSoap object for anchored networks.
	 */
	private AnatSoap getAnchoredSoap( List<String> nodes, List<List<String>> edges,
			List<Double> nodeWeights, List<Double> edgeWeights, List<Boolean> directions,
			List<String> querySources, List<String> queryTargets ) throws JAXBException {
		return new AnatSoap("network", // networkName
				nodes,
				edges,
				directions,
				nodeWeights,
				edgeWeights,
				0.5, // defaultConfidence
				querySources,
				queryTargets,
				subAlgoRadioGroup.getSelection().getActionCommand().equals("Approximate"),
				(Integer) edgePenalty.getValue(),
				(Integer) margin.getValue(),
				enableNodePenalty.isSelected() ? (Integer) curvature.getValue() : null,
				enableNodePenalty.isSelected() ? (Integer) dominance.getValue() : null,
				(Double) alpha.getValue(),
				completion.isSelected(),
				false, // predictTF
				propagate.isSelected(),
				false, // terminalsToAnchors
				null); //homogeneousWeight
	}
	
	/*
	 * Create an AnatSoap object for general networks.
	 */
	private AnatSoap getGeneralSoap( List<String> nodes, List<List<String>> edges,
			List<Double> nodeWeights, List<Double> edgeWeights, List<Boolean> directions,
			List<String> nodesSet ) throws JAXBException {
		return new AnatSoap("network", // networkName
				nodes,
				edges,
				directions,
				nodeWeights,
				edgeWeights,
				0.5, // defaultConfidence for nodes
				nodesSet,
				(Integer) edgePenalty.getValue(),
				(Integer) margin.getValue(),
				(Integer) granularity.getValue(),
				null); //homogeneousWeight
	}
	
	/*
	 * Create an AnatSoap object for local networks.
	 */
	private AnatSoap getLocalSoap( List<String> nodes, List<List<String>> edges,
			List<Double> nodeWeights, List<Double> edgeWeights, List<Boolean> directions,
			List<String> nodesSet ) throws JAXBException {
		return new AnatSoap("network", // networkName
				nodes,
				edges,
				directions,
				nodeWeights,
				edgeWeights,
				0.5, // defaultConfidence for nodes
				nodesSet,
				(Integer) degree.getValue() );
	}
	
	/*
	 * Create an AnatSoap object for shortest-paths networks.
	 */
	private AnatSoap getShortestSoap( List<String> nodes, List<List<String>> edges,
			List<Double> nodeWeights, List<Double> edgeWeights, List<Boolean> directions,
			Map<String, String> extremityPathNodes ) throws JAXBException {
		return new AnatSoap("network", // networkName
				nodes,
				edges,
				directions,
				nodeWeights,
				edgeWeights,
				0.5, // defaultConfidence for nodes
				extremityPathNodes,
				(Integer) edgePenalty.getValue(),
				(Integer) margin.getValue(),
				enableNodePenalty.isSelected() ? (Integer) curvature.getValue() : null,
				enableNodePenalty.isSelected() ? (Integer) dominance.getValue() : null,
				null); //homogeneousWeight
	}
	
	/*
	 * Parse the Anat response into an ExtractedNetwork object.
	 */
	private ExtractedNetwork parseAnatData( AnatSoap soap ) throws MlnAppCallerException {
		// Get list of edges
		List<List<String>> edges = new ArrayList<List<String>>();
		for (int i = 0; i < soap.getEdgeNodeNames1().size(); i++) {
			// add the edge given by Anat
			edges.add( List.of( soap.getEdgeNodeNames1().get(i), soap.getEdgeNodeNames2().get(i) )   );
			// add the opposite direction because Anat naturally sorts the ID so we loose the direction
			edges.add( List.of( soap.getEdgeNodeNames2().get(i), soap.getEdgeNodeNames1().get(i) )   );
		}
		// Create network
		ExtractedNetwork net = new ExtractedNetwork( soap.getNodeNames(), edges );
		// Add attributes
		net.addNodeAttributes( "Anat_redundancy", soap.getNodeRedundancies(), Double.class );
		net.addNodeAttributes( "Anat_significance", soap.getNodeSignifiances(), Double.class );
		net.addNodeAttributes( "Anat_status", soap.getNodeStatus(), String.class );
		net.addEdgeAttributes( "Anat_direction", soap.getEdgeDirections(), Boolean.class );
		net.addEdgeAttributes( "Anat_frequency", soap.getEdgeFrequencies(), Double.class );
		net.addEdgeAttributes( "Anat_probability", soap.getEdgeProbabilities(), Double.class );
		// Return
		return net;
	}
	
	@Override
	public String toString() { return "AnatApp"; }


}

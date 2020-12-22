package timenexus.apps;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.swing.JOptionPane;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlValue;

import timenexus.apps.AppCaller.MlnAppCallerException;

/*
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class AnatSoap {

	// URL of the ANAt server
	private String anatServerURL = "http://anat.cs.tau.ac.il/AnatWeb/AnatServer";
	// ID of the Anat session
	private UUID sessionId = UUID.randomUUID();
	// SOAP action
	String soapAction;
	// String containing the XML request to process a network
	private String xmlRequestNetwork;
	// String containing the XML request to get the results
	private String xmlRequestResults;
	// JAXB object containg the network graph within SOAP response from AnatApp
	private NetworkGraph networkGraphResponse;
	
	/*
	 * Create request from network to the server for "anchored" networks.
	 * @param network name
	 * @param list of node names
	 * @param list of lists which contain the node pair of the edge (with the direction first -> second)
	 * @param list of the direction for each node, in the order of the edge lists
	 * @param value of the default node confidence score
	 * @param list of node names which are the anchors
	 * @param list of node names which are the terminals
	 * @param is the approximate algorithm used, otherwise the exact algorithm will be used
	 * @param value of the edge penalty parameter (in %, from 0 to 100; default: 25)
	 * @param value of the margin parameter (in %, from 0 to 25; default should be 0)
	 * @param value of the curvature parameter for node penalty (from 0; default: 3)
	 * @param value of the dominance parameter for node penalty (from 0; default: 1)
	 * @param value of the alpha parameter = balance between global-local algorithm (from 0 to 0.50; default: 0.25)
	 * @param is the exact algorithm will run until full completion
	 * @param will the algorithm predict transcription factors 
	 * @param will the algorithm search for anchors using network propagation
	 * @param will the algorithm run from the terminals to anchors, instead of the default way (from anchors to terminals)
	 * @param value assigned to each edge weight instead of the edge weight from the input (from 0 to 1; default: 0.6) 
	 */
	public AnatSoap( String networkName, List<String> nodes, List<List<String>> edges, List<Boolean> directions,
			List<Double> nodeWeights, List<Double> edgeWeights,
			Double defaultConfidence,
    		List<String> anchors, List<String> terminals, Boolean approximateAlgo,
    		Integer edgePenalty, Integer margin, Integer curvature, Integer dominance, Double alpha,
			Boolean completion, Boolean predictTF, Boolean propagate, Boolean terminalsToAnchors, Double homogeneousWeight )
					throws JAXBException {
		List<NodeData> nodesData = createNodeData( nodes, nodeWeights );
		List<EdgeData> edgesData = createEdgeData( edges, directions, edgeWeights );
		Envelope envelope = new Envelope( networkName, nodesData, edgesData, defaultConfidence,
	    		anchors, terminals, sessionId, approximateAlgo,
	    		edgePenalty, margin, curvature, dominance, alpha,
				completion, predictTF, propagate, terminalsToAnchors, homogeneousWeight );
		xmlRequestNetwork = marshal( envelope );
		xmlRequestResults = marshal( new Envelope( sessionId ) );
		soapAction = "calculateExplanatorySubNetwork";
	}
	
	/*
	 * Create request from network to the server for "shortest-path" networks.
	 * @param network name
	 * @param list of node names
	 * @param list of lists which contain the node pair of the edge (with the direction first -> second)
	 * @param list of the direction for each node, in the order of the edge lists
	 * @param list of the node weights, in the order of the node lists
	 * @param list of the node weights, in the order of the edge lists
	 * @param value of the default node confidence score
	 * @param map containing a key and a value which are the first and last nodes of the path, resp.
	 * @param value of the edge penalty parameter (in %, from 0 to 100; default: 25)
	 * @param value of the margin parameter (in %, from 0 to 25; default should be 0)
	 * @param value of the curvature parameter for node penalty (from 0; default: 3)
	 * @param value of the dominance parameter for node penalty (from 0; default: 1)
	 * @param value assigned to each edge weight instead of the edge weight from the input (from 0 to 1; default: 0.6)
	 */
	public AnatSoap( String networkName, List<String> nodes, List<List<String>> edges, List<Boolean> directions,
			List<Double> nodeWeights, List<Double> edgeWeights,
    		Double defaultConfidence,
    		Map<String, String> extremityPathNodes, 
    		Integer edgePenalty, Integer margin, Integer curvature, Integer dominance, Double homogeneousWeight ) throws JAXBException {
		List<NodeData> nodesData = createNodeData( nodes, nodeWeights );
		List<EdgeData> edgesData = createEdgeData( edges, directions, edgeWeights );
		List<ExtremityNodesSet> extremityNodesSet = createExtremityNodesSet( extremityPathNodes );
		Envelope envelope = new Envelope( networkName, nodesData, edgesData,
	    		defaultConfidence,
	    		extremityNodesSet, sessionId,
	    		edgePenalty, margin, curvature, dominance, homogeneousWeight );
		xmlRequestNetwork = marshal( envelope );
		xmlRequestResults = marshal( new Envelope( sessionId ) );
		soapAction = "calculateShortestPathsSubNetwork";
	}
	
	/*
	 * Create request from network to the server for "neighbours" networks.
	 * @param network name
	 * @param list of node names
	 * @param list of lists which contain the node pair of the edge (with the direction first -> second)
	 * @param list of the direction for each node, in the order of the edge lists
	 * @param list of the node weights, in the order of the node lists
	 * @param list of the node weights, in the order of the edge lists
	 * @param value of the default node confidence score
	 * @param list of query nodes
	 * @param number of links to explore from the node set
	 */
	public AnatSoap( String networkName, List<String> nodes, List<List<String>> edges, List<Boolean> directions,
			List<Double> nodeWeights, List<Double> edgeWeights,
    		Double defaultConfidence,
    		List<String> nodesSet,
    		Integer degree ) throws JAXBException {
		List<NodeData> nodesData = createNodeData( nodes, nodeWeights );
		List<EdgeData> edgesData = createEdgeData( edges, directions, edgeWeights );
		Envelope envelope = new Envelope( networkName, nodesData, edgesData,
	    		defaultConfidence,
	    		nodesSet, sessionId,
	    		degree );
		xmlRequestNetwork = marshal( envelope );
		xmlRequestResults = marshal( new Envelope( sessionId ) );
		soapAction = "calculateNeighboursSubNetwork";
	}
	
	/*
	 * Create request from network to the server for "general" networks.
	 * @param network name
	 * @param list of node names
	 * @param list of lists which contain the node pair of the edge (with the direction first -> second)
	 * @param list of the direction for each node, in the order of the edge lists
	 * @param list of the node weights, in the order of the node lists
	 * @param list of the node weights, in the order of the edge lists
	 * @param value of the default node confidence score
	 * @param list of query nodes
	 * @param value of the edge penalty parameter (in %, from 0 to 100; default: 25)
	 * @param value of the margin parameter (in %, from 0 to 25; default should be 0)
	 * @param granularity controls the number of components found by the algorithm
	 * @param value assigned to each edge weight instead of the edge weight from the input (from 0 to 1; default: 0.6) 
	 */
	public AnatSoap( String networkName, List<String> nodes, List<List<String>> edges, List<Boolean> directions,
			List<Double> nodeWeights, List<Double> edgeWeights,
			Double defaultConfidence,
			List<String> nodesSet,
    		Integer edgePenalty, Integer margin, Integer granularity, Double homogeneousWeight ) throws JAXBException {
		List<NodeData> nodesData = createNodeData( nodes, nodeWeights );
		List<EdgeData> edgesData = createEdgeData( edges, directions, edgeWeights );
		Envelope envelope = new Envelope( networkName, nodesData, edgesData, defaultConfidence,
				nodesSet, sessionId,
	    		edgePenalty, margin, granularity, homogeneousWeight );
		xmlRequestNetwork = marshal( envelope );
		xmlRequestResults = marshal( new Envelope( sessionId ) );
		soapAction = "calculateProjectionSubNetwork";
	}

	/*
	 * Convert the list of node names to the NodeData object
	 * @param list of node names
	 * @param list of weights
	 */
	private List<NodeData> createNodeData( List<String> nodes, List<Double> weights ) {
		List<NodeData> nodesData = new ArrayList<NodeData>();
		for (int i = 0; i < nodes.size(); i++)
			nodesData.add( new NodeData( nodes.get(i), weights.get(i) ) );
		return nodesData;
	}
	
	/*
	 * Convert the list of edge names to the EdgeData object
	 * @param list of lists which contain the node pair of the edge (with the direction first -> second)
	 * @param list of the direction for each node, in the order of the edge lists
	 * @param list of weights
	 */
	private List<EdgeData> createEdgeData( List<List<String>> edges, List<Boolean> directions, List<Double> weights ) {
		List<EdgeData> edgesData = new ArrayList<EdgeData>();
		for (int i = 0; i < edges.size(); i++)
			edgesData.add( new EdgeData( edges.get(i).get(0), edges.get(i).get(1), directions.get(i), weights.get(i) ) );
		return edgesData;
	}
	
	/*
	 * Convert the list of extremity nodes shaping a path for the shortest-path algorithm.
	 * @param map containing a key and a value which are the first and last nodes of the path, resp.
	 */
	private List<ExtremityNodesSet> createExtremityNodesSet( Map<String, String> extremityPathNodes ){
		List<ExtremityNodesSet> extremityNodesSet = new ArrayList<ExtremityNodesSet>();
		for (String query : extremityPathNodes.keySet())
			extremityNodesSet.add( new ExtremityNodesSet( query, extremityPathNodes.get(query) ) );
		return extremityNodesSet;
	}
	
	/*
	 * Marshal JAXB object.
	 */
	private String marshal( Envelope envelope ) throws JAXBException {
		// Create marshaller
		JAXBContext context = JAXBContext.newInstance(Envelope.class);
		Marshaller m = context.createMarshaller();
		// Set properties
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		m.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE); // remove the header with "standalone=true"
		m.setProperty("com.sun.xml.bind.xmlHeaders", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		// Marshal 
		StringWriter sw = new StringWriter();
	    m.marshal( envelope, sw );
	    return sw.toString();
	}
	
	/*
	 * Get ID of the network.
	 */
	public UUID getID() {
		return sessionId;
	}
	
	/*
	 * Get the XML request to process the network.
	 */
	public String getXmlRequestForNetwork() {
		return xmlRequestNetwork;
	}
	
	/*
	 * Get the XML request to get results.
	 */
	public String getXmlRequestForResults() {
		return xmlRequestResults;
	}
	
	/*
	 * Unmarshal XML response from Anat SOAP webservice.
	 * @param XML response
	 * @return true if parsing was successfull, false otherwise
	 */
	public boolean parseAnatResponse(String xml) throws JAXBException {
		// Remove namespace from the string
		xml = xml.replace("<S:", "<");
		xml = xml.replace("</S:", "</");
		xml = xml.replace("<ns2:", "<");
		xml = xml.replace("</ns2:", "</");
		// Create unmarshaller
		JAXBContext jaxbContext = JAXBContext.newInstance(EnvelopeResponse.class);
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		// Parse the XML
		EnvelopeResponse response = (EnvelopeResponse) unmarshaller.unmarshal( new StringReader(xml) );
		// Return the object
		networkGraphResponse = response.getBodyParseResponse().getNetworkGraph();
		return ! isEmptyNetworkReponse();
	}
	
	/*
	 * Get SOAP action.
	 */
	public String getSoapAction() { return soapAction; }
	
	/*
	 * Get SOAP action for results.
	 */
	public String getSoapActionResults() { return "getResult"; }
	
	/*
	 * Get URL of the Anat Server.
	 */
	public String getAnatServerURL() { return anatServerURL; }
	
	
	
	/*_______________________________________
	 * 
	 *			CREATE REQUEST
	 *_______________________________________
	 */
	
	/*
	 * Envelope element of the SOAP request.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "S:Envelope")
	private static class Envelope {
		
		@XmlAttribute(name="xmlns:S")
		private String xmlns = "http://schemas.xmlsoap.org/soap/envelope/";
		
		@XmlElements({
			@XmlElement(name="S:Body", type=BodyAnchoredNetworkParams.class),
			@XmlElement(name="S:Body", type=BodyShortestPathParams.class),
			@XmlElement(name="S:Body", type=BodyNeighboursParams.class),
			@XmlElement(name="S:Body", type=BodyGeneralNetworkParams.class),
			@XmlElement(name="S:Body", type=BodyGetResults.class)
		})
		private Body body;
		
		/*
		 * Create request from network to the server for "anchored" networks.
		 */
		public Envelope( String networkName, List<NodeData> nodesData, List<EdgeData> edgesData, Double defaultConfidence,
	    		List<String> anchors, List<String> terminals, UUID sessionId, Boolean approximateAlgo,
	    		Integer edgePenalty, Integer margin, Integer curvature, Integer dominance, Double alpha,
	    		Boolean completion, Boolean predictTF, Boolean propagate, Boolean terminalsToAnchors, Double homogeneousWeight ) {
			body = new BodyAnchoredNetworkParams( networkName, nodesData, edgesData, defaultConfidence,
		    		anchors, terminals, sessionId, approximateAlgo,
		    		edgePenalty, margin, curvature, dominance, alpha,
					completion, predictTF, propagate, terminalsToAnchors, homogeneousWeight );
		}
		
		/*
		 * Create request from network to the server for "shortest-path" networks.
		 */
		public Envelope( String networkName, List<NodeData> nodesData, List<EdgeData> edgesData,
				Double defaultConfidence,
	    		List<ExtremityNodesSet> extremityNodesSet, UUID sessionId,
	    		Integer edgePenalty, Integer margin, Integer curvature, Integer dominance, Double homogeneousWeight ) {
			body = new BodyShortestPathParams( networkName, nodesData, edgesData,
		    		defaultConfidence,
		    		extremityNodesSet, sessionId,
		    		edgePenalty, margin, curvature, dominance, homogeneousWeight );
		}
		
		/*
		 * Create request from network to the server for "neighbours" networks.
		 */
		public Envelope( String networkName, List<NodeData> nodesData, List<EdgeData> edgesData,
				Double defaultConfidence,
	    		List<String> nodesSet, UUID sessionId,
	    		Integer degree ) {
			body = new BodyNeighboursParams( networkName, nodesData, edgesData,
		    		defaultConfidence,
		    		nodesSet, sessionId,
		    		degree );
		}
		
		/*
		 * Create request from network to the server for "general" networks.
		 */
		public Envelope( String networkName, List<NodeData> nodesData, List<EdgeData> edgesData, Double defaultConfidence,
				List<String> nodesSet, UUID sessionId,
	    		Integer edgePenalty, Integer margin, Integer granularity, Double homogeneousWeight ) {
			body = new BodyGeneralNetworkParams( networkName, nodesData, edgesData, defaultConfidence,
					nodesSet, sessionId,
		    		edgePenalty, margin, granularity, homogeneousWeight );
		}
		
		/*
		 * Get results from the server.
		 */
		public Envelope( UUID sessionId ) {
			body = new BodyGetResults( sessionId );
		}
		
		@SuppressWarnings("unused")
		public Envelope() {}
	}
	
	/*
	 * Envelope of the SOAP response containing the subnetwork.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "Envelope")
	private static class EnvelopeResponse {
		
		/*@XmlAttribute(name="xmlns:S")
		private String xmlns = "http://schemas.xmlsoap.org/soap/envelope/";*/
		
		@XmlElement(name="Body")
		private BodyParseResponse bodyParseResponse;

		public BodyParseResponse getBodyParseResponse() {
			return bodyParseResponse;
		}
	}
	
	/*
	 * Body element of the SOAP request for explanatory parameters.
	 */
	@XmlTransient
	private static abstract class Body{};
	
	/*_______________________________________
	 * 
	 *			MAKE DATA TO SEND
	 *_______________________________________
	 */
	
	/*
	 * Body element of the SOAP request for "anchored network" option parameters.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name="S:Body")
	private static class BodyAnchoredNetworkParams extends Body {		
		@XmlElement(name="ns2:explanatoryParameters")
		private Parameters params;

	    public BodyAnchoredNetworkParams( String networkName, List<NodeData> nodesData, List<EdgeData> edgesData,
	    		Double defaultConfidence,
	    		List<String> anchors, List<String> terminals, UUID sessionId, Boolean approximateAlgo,
	    		Integer edgePenalty, Integer margin, Integer curvature, Integer dominance, Double alpha,
				Boolean completion, Boolean predictTF, Boolean propagate, Boolean terminalsToAnchors, Double homogeneousWeight ) {
	    	params = new Parameters(networkName, nodesData, edgesData, defaultConfidence,
		    		anchors, terminals, sessionId, approximateAlgo,
		    		edgePenalty, margin, curvature, dominance, alpha,
					completion, predictTF, propagate, terminalsToAnchors, homogeneousWeight);
	    }
	    
	    @SuppressWarnings("unused")
		public BodyAnchoredNetworkParams() {}
	}
	
	/*
	 * Body element of the SOAP request for "shortest paths" option parameters.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name="S:Body")
	private static class BodyShortestPathParams extends Body {		
		@XmlElement(name="ns2:shortestPathsParams")
		private Parameters params;

	    public BodyShortestPathParams( String networkName, List<NodeData> nodesData, List<EdgeData> edgesData,
	    		Double defaultConfidence,
	    		List<ExtremityNodesSet> extremityNodesSet, UUID sessionId,
	    		Integer edgePenalty, Integer margin, Integer curvature, Integer dominance, Double homogeneousWeight ) {
	    	params = new Parameters( networkName, nodesData, edgesData,
		    		defaultConfidence,
		    		extremityNodesSet, sessionId,
		    		edgePenalty, margin, curvature, dominance, homogeneousWeight );
	    }
	    
	    @SuppressWarnings("unused")
		public BodyShortestPathParams() {}
	}
	
	/*
	 * Body element of the SOAP request for "neighbours" option parameters.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name="S:Body")
	private static class BodyNeighboursParams extends Body {		
		@XmlElement(name="ns2:neighbourParams")
		private Parameters params;

	    public BodyNeighboursParams( String networkName, List<NodeData> nodesData, List<EdgeData> edgesData,
	    		Double defaultConfidence,
	    		List<String> neighbourNodesSet, UUID sessionId,
	    		Integer degree ) {
	    	params = new Parameters( networkName, nodesData, edgesData,
		    		defaultConfidence,
		    		neighbourNodesSet, sessionId,
		    		degree );
	    }
	    
	    @SuppressWarnings("unused")
		public BodyNeighboursParams() {}
	}
	
	/*
	 * Body element of the SOAP request for "general network" option parameters.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name="S:Body")
	private static class BodyGeneralNetworkParams extends Body {		
		@XmlElement(name="ns2:projectionParams")
		private Parameters params;

	    public BodyGeneralNetworkParams( String networkName, List<NodeData> nodesData, List<EdgeData> edgesData, Double defaultConfidence,
				List<String> nodesSet, UUID sessionId,
	    		Integer edgePenalty, Integer margin, Integer granularity, Double homogeneousWeight ) {
	    	params = new Parameters( networkName, nodesData, edgesData, defaultConfidence,
					nodesSet, sessionId,
		    		edgePenalty, margin, granularity, homogeneousWeight );
	    }
	    
	    @SuppressWarnings("unused")
		public BodyGeneralNetworkParams() {}
	}
	
	/*
	 * Parameters for the algorithm.
	 */
	private static class Parameters {
		/* Common parameters */
		
		@XmlAttribute(name="xmlns:ns2")
		private String xmlns = "network";

		@XmlElement(name="ns2:backGroundNetwork")
		private BackgroundNetwork backgroundNetwork;
		
		@XmlElement(name="ns2:baseNetworkFileName")
		private String baseNetworkFileName = "E_empty.net";
		
		@XmlElement(name="ns2:edgeConstraintMap")
		private String edgeConstraintMap = "";
		
		@XmlElement(name="ns2:nodeConstraintMap")
		private String nodeConstraintMap = "";
		
		@XmlElement(name="ns2:sessionId")
		private UUID sessionId;
		
		@XmlElement(name="ns2:title")
		private String title = "";
		
		@XmlElement(name="ns2:lengthPenalty")
		private Integer edgePenalty;
		
		@XmlElement(name="ns2:margin")
		private Integer margin;
		
		@XmlElement(name="ns2:curvature")
		private Integer curvature;
		
		@XmlElement(name="ns2:dominance")
		private Integer dominance;
		
		@XmlElement(name="ns2:algorithmType")
		private String algorithmType;
		
		@XmlElement(name="ns2:subAlgorithm")
		private String subAlgorithm; // "APPROXIMATE" or "EXACT" for anchored, "CLUSTERING" for general networks
		
		@XmlElement(name="ns2:homogeneousWeight")
		private Double homogeneousWeight;
		
		/* Parameters specific to "anchored" algorithm */
		
		@XmlElements({
			 @XmlElement(name = "ns2:anchors", type = String.class)
		})
		private List<String> anchors;
		
		@XmlElements({
			 @XmlElement(name = "ns2:terminals", type = String.class)
		})
		private List<String> terminals;
		
		@XmlElement(name="ns2:alpha")
		private Double alpha; // balance local-global
		
		@XmlElement(name="ns2:completion")
		private Boolean completion;
		
		@XmlElement(name="ns2:predictTF")
		private Boolean predictTF;
		
		@XmlElement(name="ns2:propagate")
		private Boolean propagate;
		
		@XmlElement(name="ns2:terminalsToAnchors")
		private Boolean terminalsToAnchors;
		
		/* Parameters specific to "shortest-path" algorithm */
		
		@XmlElements({
			@XmlElement(name="ns2:set", type=ExtremityNodesSet.class)
		})
		private List<ExtremityNodesSet> extremityNodesSet;
		
		/* Parameters specific to "neighbours" algorithm */
		
		@XmlElement(name="ns2:degree")
		private Integer degree;
		
		/* Parameters specific to "general network" algorithm */
		
		@XmlElement(name="ns2:granularity")
		private Integer granularity;
		
		@XmlElements({
			@XmlElement(name="ns2:set", type=String.class)
		})
		private List<String> nodesSet;
		
		
		/*
		 * Parameters for "anchored" network.
		 */
	    public Parameters( String networkName, List<NodeData> nodesData, List<EdgeData> edgesData,
	    		Double defaultConfidence,
	    		List<String> anchors, List<String> terminals, UUID sessionId, Boolean approximateAlgo,
	    		Integer edgePenalty, Integer margin, Integer curvature, Integer dominance, Double alpha,
				Boolean completion, Boolean predictTF, Boolean propagate, Boolean terminalsToAnchors,
				Double homogeneousWeight ) {
	    	algorithmType = "EXPLANATORYPATHWAYS";
	    	backgroundNetwork = new BackgroundNetwork( networkName, nodesData, edgesData, defaultConfidence );
	    	this.anchors = anchors;
	    	this.terminals = terminals;
	    	this.sessionId = sessionId;
	    	this.subAlgorithm = approximateAlgo ? "APPROXIMATE" : "EXACT";
			this.edgePenalty = edgePenalty;
			this.margin = margin;
			this.alpha = alpha;
			this.curvature = curvature;
			this.dominance = dominance;
			this.completion = completion;
			this.predictTF = predictTF;
			this.propagate = propagate;
			this.terminalsToAnchors = terminalsToAnchors;
			this.homogeneousWeight = homogeneousWeight;
	    }
	    
	    /*
	     * Parameters for "shortest-path" network.
	     */
	    public Parameters( String networkName, List<NodeData> nodesData, List<EdgeData> edgesData,
	    		Double defaultConfidence,
	    		List<ExtremityNodesSet> extremityNodesSet, UUID sessionId,
	    		Integer edgePenalty, Integer margin, Integer curvature, Integer dominance,
	    		Double homogeneousWeight ) {
	    	algorithmType = "SHORTESTPATHS";
	    	backgroundNetwork = new BackgroundNetwork( networkName, nodesData, edgesData, defaultConfidence );
	    	this.extremityNodesSet = extremityNodesSet;
	    	this.sessionId = sessionId;
			this.edgePenalty = edgePenalty;
			this.margin = margin;
			this.curvature = curvature;
			this.dominance = dominance;
			this.homogeneousWeight = homogeneousWeight;
	    }
	    
	    /*
	     * Parameters for "neighbours" network.
	     */
	    public Parameters( String networkName, List<NodeData> nodesData, List<EdgeData> edgesData,
	    		Double defaultConfidence,
	    		List<String> nodesSet, UUID sessionId,
	    		Integer degree ) {
	    	algorithmType = "NEIGHBOURS";
	    	backgroundNetwork = new BackgroundNetwork( networkName, nodesData, edgesData, defaultConfidence );
	    	this.nodesSet = nodesSet;
	    	this.sessionId = sessionId;
			this.degree = degree;
	    }
	    
	    /*
	     * Parameters for "general" network.
	     */
	    public Parameters( String networkName, List<NodeData> nodesData, List<EdgeData> edgesData, Double defaultConfidence,
				List<String> nodesSet, UUID sessionId,
	    		Integer edgePenalty, Integer margin, Integer granularity, Double homogeneousWeight ) {
	    	this.algorithmType = "PROJECTIONANALYSIS";
	    	this.subAlgorithm = "CLUSTERING";
	    	this.backgroundNetwork = new BackgroundNetwork( networkName, nodesData, edgesData, defaultConfidence );
	    	this.nodesSet = nodesSet;
	    	this.sessionId = sessionId;
	    	this.edgePenalty = edgePenalty;
	    	this.margin = margin;
			this.granularity = granularity;
			this.homogeneousWeight = homogeneousWeight;
	    }
	}
	
	/*
	 * Background network
	 */
	private static class BackgroundNetwork{
		@XmlElement(name="ns2:defaultConfidence")
		private Double defaultConfidence;
		
		@XmlElement(name="ns2:networkName")
		private String networkName;
		
		@XmlElements({
			 @XmlElement(name = "ns2:edgesData", type = EdgeData.class),
		})
		private List<EdgeData> edgesData;
		
		@XmlElements({
			 @XmlElement(name = "ns2:nodesData", type = NodeData.class),
		})
		private List<NodeData> nodesData;
		
		public BackgroundNetwork( String networkName, List<NodeData> nodesData, List<EdgeData> edgesData,
				Double defaultConfidence ) {
			this.networkName = networkName;
			this.nodesData = nodesData;
			this.edgesData = edgesData;
			this.defaultConfidence = defaultConfidence;
		}
	}
	
	/*
	 * Edge data = define one edge.
	 */
	private static class EdgeData{
		@XmlElement(name="ns2:action")
		private String action; //SET_DIRECTED or SET_UNDIRECTED
		
		@XmlElement(name="ns2:additionalInfo")
		private String info = "";
		
		@XmlElement(name="ns2:fromNodeId")
		private String fromNodeId;
		
		@XmlElement(name="ns2:toNodeId")
		private String toNodeId;
		
		@XmlElement(name="ns2:confidence")
		private Double confidence;
		
		public EdgeData( String fromNodeId, String toNodeId, Boolean isDirected, Double confidence ) {
			this.fromNodeId = fromNodeId;
			this.toNodeId = toNodeId;
			this.action = isDirected ? "SET_DIRECTED" : "SET_UNDIRECTED";
			this.confidence = confidence;
		}
	}
	
	/*
	 * Node data = define one node.
	 */
	private static class NodeData{
		@XmlElement(name="ns2:operation")
		private String operation = "ADD";
		
		@XmlElement(name="ns2:nodeId")
		private String nodeId;
		
		@XmlElement(name="ns2:confidence")
		private Double confidence;
		
		public NodeData( String nodeId, Double confidence ) {
			this.nodeId = nodeId;
			this.confidence = confidence;
		}
	}
	
	/*
	 * Set element containing first and second nodes for shortest paths.
	 */
	private static class ExtremityNodesSet{
		@XmlElement(name="ns2:first")
		private ExtremityNode firstNode;
		
		@XmlElement(name="ns2:second")
		private ExtremityNode secondNode;
		
		public ExtremityNodesSet( String firstNode, String secondNode ) {
			this.firstNode = new ExtremityNode(firstNode);
			this.secondNode = new ExtremityNode(secondNode);
		}
	}
	
	/*
	 * Node at one of the extremity of a shortest path.
	 */
	private static class ExtremityNode{
		@XmlAttribute(name="xmlns:xsi")
		private String xmlnsxsi = "http://www.w3.org/2001/XMLSchema-instance";
		
		@XmlAttribute(name="xmlns:xs")
		private String xmlnsxs = "http://www.w3.org/2001/XMLSchema";
		
		@XmlAttribute(name="xsi:type")
		private String type = "xs:string";
		
		@XmlValue
		private String node;
		
		public ExtremityNode(String node) {
			this.node = node;
		}
	}
	
	/*_______________________________________
	 * 
	 *			GET RESULTS
	 *_______________________________________
	 */
	
	/*
	 * Body element of the SOAP request to get results from the server.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name="S:Body")
	private static class BodyGetResults extends Body {		
		@XmlElement(name="ns2:sessionId")
		private SessionId sessionId;

	    public BodyGetResults( UUID sessionId ) {
	    	this.sessionId = new SessionId( sessionId );
	    }
	    
	    @SuppressWarnings("unused")
		public BodyGetResults() {}
	}
	
	/*
	 * Session ID field of the SOAP request
	 */
	private static class SessionId{
		@XmlAttribute(name="xmlns:ns2")
		private String xmlns = "network";
		
		@XmlValue
		private UUID sessionId;
		
		public SessionId( UUID sessionId ) {
			this.sessionId = sessionId;
		}
		
	}
	
	/*_______________________________________
	 * 
	 *			PARSE RESULTS
	 *_______________________________________
	 */
	
	/*
	 * Body element of the SOAP response with the results .
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name="Body")
	private static class BodyParseResponse {		
		@XmlElement(name="networkGraph")
		private NetworkGraph networkGraph;

		public NetworkGraph getNetworkGraph() {
			return networkGraph;
		}
	}
	
	/*
	 * 
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name="Body")
	private static class NetworkGraph {
		@XmlElements({
			@XmlElement(name="edges", type=ResponseEdge.class)
		})
		private List<ResponseEdge> responseEdges;
		
		@XmlElements({
			@XmlElement(name="nodes", type=ResponseNode.class)
		})
		private List<ResponseNode> responseNodes;
		
		@XmlElements({
			@XmlElement(name="warnings", type=Messages.class)
		})
		private List<Messages> warnings;
		
		@XmlElements({
			@XmlElement(name="errors", type=Messages.class)
		})
		private List<Messages> errors;
		
		/* GETTERS FOR ATTTRIBUTES */ 
		
		public boolean isEmpty() {
			if ( responseNodes != null ) return false;
			return true;
		}
		
		public List<ResponseEdge> getResponseEdges() {
			return responseEdges;
		}

		public List<ResponseNode> getResponseNodes() {
			return responseNodes;
		}
	
		public List<Messages> getWarnings(){
			return warnings;
		}
		
		public List<Messages> getErrors(){
			return errors;
		}
		
		/* GETTERS FOR NETWORK ELEMENTS */
		
		public <T> List<T> getNodeValues(Function<ResponseNode, T> func) throws MlnAppCallerException {
			// Manage potential empty response
			if ( getResponseNodes() == null )
				throw new MlnAppCallerException("Anat did not return any nodes. Extraction was aborted.",
						"App calling error", JOptionPane.ERROR_MESSAGE );
			// Return the properties of a node accoding to func
			List<T> names = new ArrayList<T>();
			for ( ResponseNode node : getResponseNodes() )
				names.add( func.apply(node) );
			return names;
		}
		
		public <T> List<T> getEdgeValues(Function<ResponseEdge, T> func) throws MlnAppCallerException {
			// Manage potential empty response
			if ( getResponseEdges() == null )
				throw new MlnAppCallerException("Anat did not return any edges. Extraction was aborted.",
						"App calling error", JOptionPane.ERROR_MESSAGE );
			// Return the properties of a node accoding to func
			List<T> names = new ArrayList<T>();
			for ( ResponseEdge edge : getResponseEdges() )
				names.add( func.apply(edge) );
			return names;
		}

	}
	
	/*
	 * Edge element within the network provided by the SOAP response.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name="Body")
	private static class ResponseEdge {
		@XmlElement(name="directed")
		private Boolean directed;
		
		@XmlElement(name="frequency")
		private Double frequency;
		
		@XmlElement(name="id1")
		private String id1;
		
		@XmlElement(name="id2")
		private String id2;
		
		@XmlElement(name="probability")
		private Double probability;
		
		@XmlElement(name="pubMedIDs")
		private String pubMedIDs;
		
		public Boolean getDirected() {
			return directed;
		}

		public Double getFrequency() {
			return frequency;
		}

		public String getId1() {
			return id1;
		}

		public String getId2() {
			return id2;
		}
		
		public Double getProbability() {
			return probability;
		}

		public String getPubMedIDs() {
			return pubMedIDs;
		}
	}
	
	/*
	 * Node element within the network provided by the SOAP response.
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name="Body")
	private static class ResponseNode {
		@XmlElement(name="redundancy")
		private Double redundancy;
		
		@XmlElement(name="significance")
		private Double significance;
		
		@XmlElement(name="id")
		private String id;
		
		@XmlElement(name="status")
		private String status;

		public Double getRedundancy() {
			return redundancy;
		}
		
		public Double getSignificance() {
			return significance;
		}

		public String getId() {
			return id;
		}

		public String getStatus() {
			return status;
		}
	}
	
	/*
	 * Get errors and warnings
	 */
	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement
	private static class Messages{
		@XmlElements({
			@XmlElement(name="message", type=String.class)
		})
		private List<String> listMessages;
		
		public List<String> getListMessages(){
			return listMessages;
		}
		
	}
	
	/*_______________________________________
	 * 
	 *			GETTERS FOR RESULTS
	 *_______________________________________
	 */
	
	/*
	 * Test if the subnetwork from Anat Server is empty.
	 * @return true if the subnetwork doesn't exist or is empty
	 */
	public boolean isEmptyNetworkReponse() {
		if ( networkGraphResponse != null ) return networkGraphResponse.isEmpty();
		return true;
	}
	
	/*
	 * Get list of warnings of the Anat results.
	 */
	public List<String> getWarnings(){
		List<String> warnings = new ArrayList<String>();
		if ( networkGraphResponse != null && networkGraphResponse.getWarnings() != null ){
			for ( Messages w : networkGraphResponse.getWarnings() )
				for ( String msg : w.getListMessages() ) warnings.add(msg);
		}
		return warnings;
	}
	
	/*
	 * Get list of errors of the Anat results.
	 */
	public List<String> getErrors(){
		List<String> errors = new ArrayList<String>();
		if ( networkGraphResponse != null && networkGraphResponse.getErrors() != null ){
			for ( Messages w : networkGraphResponse.getErrors() )
				for ( String msg : w.getListMessages() ) errors.add(msg);
		}
		return errors;
	}
	
	/*
	 * Get node names of the subnetwork from Anat.
	 */
	public List<String> getNodeNames() throws MlnAppCallerException{
		return networkGraphResponse.getNodeValues(ResponseNode::getId);
	}
	
	/*
	 * Get node significance of the subnetwork from Anat.
	 */
	public List<Double> getNodeSignifiances() throws MlnAppCallerException{
		return networkGraphResponse.getNodeValues(ResponseNode::getSignificance);
	}
	
	/*
	 * Get node significance of the subnetwork from Anat.
	 */
	public List<String> getNodeStatus() throws MlnAppCallerException{
		return networkGraphResponse.getNodeValues(ResponseNode::getStatus);
	}
	
	/*
	 * Get node redundancy of the subnetwork from Anat.
	 */
	public List<Double> getNodeRedundancies() throws MlnAppCallerException{
		return networkGraphResponse.getNodeValues(ResponseNode::getRedundancy);
	}
	
	/*
	 * Get 1st node name of edges from the subnetwork from Anat.
	 */
	public List<String> getEdgeNodeNames1() throws MlnAppCallerException{
		return networkGraphResponse.getEdgeValues(ResponseEdge::getId1);
	}
	
	/*
	 * Get 2nd node name of edges from the subnetwork from Anat.
	 */
	public List<String> getEdgeNodeNames2() throws MlnAppCallerException{
		return networkGraphResponse.getEdgeValues(ResponseEdge::getId2);
	}
	
	/*
	 * Get edge directions of the subnetwork from Anat.
	 */
	public List<Boolean> getEdgeDirections() throws MlnAppCallerException{
		return networkGraphResponse.getEdgeValues(ResponseEdge::getDirected);
	}
	
	/*
	 * Get edge frequencies of the subnetwork from Anat.
	 */
	public List<Double> getEdgeFrequencies() throws MlnAppCallerException{
		return networkGraphResponse.getEdgeValues(ResponseEdge::getFrequency);
	}
	
	/*
	 * Get edge frequencies of the subnetwork from Anat.
	 */
	public List<Double> getEdgeProbabilities() throws MlnAppCallerException{
		return networkGraphResponse.getEdgeValues(ResponseEdge::getProbability);
	}
	
	/*
	 * Get PubMed IDs of the subnetwork from Anat.
	 */
	public List<String> getPubMedIDs() throws MlnAppCallerException{
		return networkGraphResponse.getEdgeValues(ResponseEdge::getPubMedIDs);
	}
}
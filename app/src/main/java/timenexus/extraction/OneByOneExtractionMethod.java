package timenexus.extraction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComboBox;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyRow;
import org.cytoscape.work.TaskMonitor;

import timenexus.apps.ExtractedNetwork;
import timenexus.apps.AppCaller.MlnAppCallerException;
import timenexus.temporalnetwork.CopyFlattenedNetworkPanel;
import timenexus.temporalnetwork.MlnWriter;
import timenexus.utils.ServiceProvider;

/*
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class OneByOneExtractionMethod extends ExtractionMethod {

	public OneByOneExtractionMethod() {}

	@Override
	public String toString() { return "TimeNexus One-By-One extraction"; }
	
	@Override
	public void run(TaskMonitor taskMonitor) throws MlnExtractionException, MlnAppCallerException {
		//long startTime = System.nanoTime();
		
		taskMonitor.setTitle(toString() + " with " + app);
		CyNetworkManager networkManager = ServiceProvider.get(CyNetworkManager.class);
		
		//		Checking that the multi-layer network meet the app's criteria
		if ( isCheckEnabled() ) {
			taskMonitor.setStatusMessage("Checking the input data...");
			// Check that list of layers has adjacent layers without gaps
			checkListOfLayers(layers);
			// Check that the multi-layer network fit the app's criteria
			checkMultiLayerNetwork();
		}
		
		//		Get the names of query columns
		HashMap<Integer, JComboBox<String>> queryColNames = app.getQueryColNames();
		//		Get nodes of the network
		// Create list of nodes per layer
		Map<Integer, Set<String>> nodeNames = new HashMap<Integer, Set<String>>();
		for ( int idLayer : layers ) nodeNames.put( idLayer,  new HashSet<String>() );
		// Get nodes
		for ( CyRow row : flattenedNet.getDefaultNodeTable().getAllRows() ) {
			int idLayer = row.get( MlnWriter.LAYER_ID, Integer.class );
			if ( layers.contains( idLayer ) ) nodeNames.get( idLayer ).add( row.get(CyNetwork.NAME, String.class) );
		}
		
		//		Call the app for each subnetwork
		networksToExtract = new ArrayList<CyNetwork>();
		Set<String> queryNodeLayers = new HashSet<String>();
		List<ExtractedNetwork> extractedNetworks = new ArrayList<ExtractedNetwork>();
		Set<String> extractedNetNodes = new HashSet<String>();
		for ( int k = 0; k < layers.size() ; k++ ) {
			int idLayer = layers.get(k);
			// Get subset of the multi-layer network
			taskMonitor.setStatusMessage("Building a network for the layer " + idLayer + "...");
			CyNetwork subLayerNet = CopyFlattenedNetworkPanel.copyLayers( flattenedNet,
					List.of(idLayer), nodeNames.get(idLayer), "temporary_network" );
			networksToExtract.add(subLayerNet);
			networkManager.addNetwork(subLayerNet);
			// Get query sources and query targets
			String queryCol = (String) queryColNames.get(idLayer).getSelectedItem();
			Map<String, String> queryNodes = getQueryNodesFromLayer( subLayerNet, idLayer, queryCol );
			queryNodeLayers.addAll( queryNodes.keySet() );
			// Call
			ExtractedNetwork net = app.call( subLayerNet, queryNodes, queryNodes, taskMonitor, this );
			extractedNetworks.add( net );
			extractedNetNodes.addAll( net.getNodeNames() );
			// Destroy 2-layer networks when done
			networkManager.destroyNetwork(subLayerNet);
			taskMonitor.setProgress( ((double) (k+1)) / layers.size() );
			// Cancel the task
			checkCancelling();
		}
		
		//		Generate a new multi-layer network from the list of nodes
		taskMonitor.setStatusMessage("Generating sub-multi-layer network...");
		// Create flattened network
		CyNetwork extractedFlatNet = CopyFlattenedNetworkPanel.copyLayers( flattenedNet, layers, extractedNetNodes, "Extracted network" );
		networkManager.addNetwork(extractedFlatNet);
		// Add node and edge attributes to the network
		for (ExtractedNetwork extractedNet : extractedNetworks) {
			checkCancelling();
			addAtributesToFlatNetwork( extractedNet, extractedFlatNet );
		}
		// Add 'isQuery' column
		addIsQueryColumn( queryNodeLayers, extractedFlatNet );
		// Create multi-layer network
		MlnWriter.createMLNFromFlatNetwork( extractedFlatNet, layers );
		
		/*long sec = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime);
		try(BufferedWriter writer = new BufferedWriter(
				new FileWriter("/home/pierrelee/Documents/temporal_network_project/#paper/time.txt", true))){
			writer.append(toString() + "\t" + app + "\t"+ flattenedNet.getNodeCount() +"\t" + sec + "\n");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/

	}
}

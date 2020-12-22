package timenexus.extraction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
public class GlobalExtractionMethod extends ExtractionMethod {

	public GlobalExtractionMethod() {}

	@Override
	public String toString() { return "TimeNexus global extraction"; }
	
	@Override
	public void run(TaskMonitor taskMonitor) throws MlnExtractionException, MlnAppCallerException {
		//long startTime = System.nanoTime();
		
		taskMonitor.setTitle(toString() + " with " + app);
		
		//		Checking that the multi-layer network meet the app's criteria
		if ( isCheckEnabled() ) {
			taskMonitor.setStatusMessage("Checking the input data...");
			// Check that list of layers has adjacent layers without gaps
			checkListOfLayers(layers);
			// Check that the multi-layer network fit the app's criteria
			checkMultiLayerNetwork();
		}
		
		//		Copy the flattened network into a temporary network
		// Get nodes of the network
		taskMonitor.setStatusMessage("Copying the flattened network...");
		Set<String> nodeLayers = new HashSet<String>();
		for ( CyRow row : flattenedNet.getDefaultNodeTable().getAllRows() )
			nodeLayers.add( row.get( CyNetwork.NAME, String.class ) );
		// Copy the network 
		CyNetwork networkToExtract = CopyFlattenedNetworkPanel.copyLayers( flattenedNet, layers, nodeLayers, "temporary_network" );
		networksToExtract = new ArrayList<CyNetwork>();
		networksToExtract.add(networkToExtract);
		ServiceProvider.get(CyNetworkManager.class).addNetwork(networkToExtract);
		
		//		Call the app
		int idLayer1 = layers.get(0);
		int idLayerN = layers.get( layers.size() - 1 );
		// Get query sources and query targets
		HashMap<Integer, JComboBox<String>> queryColNames = app.getQueryColNames();
		String querySourceCol = (String) queryColNames.get(idLayer1).getSelectedItem();
		String queryTargetCol = (String) queryColNames.get(idLayerN).getSelectedItem();
		Map<String, String> querySources = getQueryNodesFromLayer( networkToExtract, idLayer1, querySourceCol );
		Map<String, String> queryTargets = getQueryNodesFromLayer( networkToExtract, idLayerN, queryTargetCol );
		// Call
		ExtractedNetwork extractedNetwork = app.call( networkToExtract, querySources, queryTargets, taskMonitor, this );
		Set<String> extractedNetNodes = new HashSet<String>( extractedNetwork.getNodeNames() );
		// Destroy temporary network
		ServiceProvider.get(CyNetworkManager.class).destroyNetwork(networkToExtract);
		
		//		Generate a new multi-layer network from the list of nodes
		checkCancelling();
		taskMonitor.setStatusMessage("Generating sub-multi-layer network...");
		// Create flattened network
		CyNetwork extractedFlatNet = CopyFlattenedNetworkPanel.copyLayers( flattenedNet, layers, extractedNetNodes, "Extracted network" );
		ServiceProvider.get(CyNetworkManager.class).addNetwork(extractedFlatNet);
		// Add node and edge attributes to the network
		addAtributesToFlatNetwork( extractedNetwork, extractedFlatNet );
		// Add 'isQuery' column
		Set<String> queryNodeLayers = new HashSet<String>( querySources.keySet() );
		queryNodeLayers.addAll( queryTargets.keySet() );
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

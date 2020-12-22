package timenexus.temporalnetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.util.ListSingleSelection;

import timenexus.utils.MlnException;
import timenexus.utils.ServiceProvider;

/*
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class MlnBuildFromFlatNetworkPanel extends AbstractTask {
	
	@Tunable(description="Select an independent flattened network to convert:",
			tooltip="A flattened network should have been loaded into Cytoscape. "
					+ "Then here, select it to convert it to a multilayer network.")
	public ListSingleSelection<CyNetwork> availableNetworks = new ListSingleSelection<CyNetwork>();
	
	// Create empty network to trigger the list selection
	CyNetwork emptyNetwork = ServiceProvider.get(CyNetworkFactory.class).createNetwork();
	
	public MlnBuildFromFlatNetworkPanel() {
		List<CyNetwork> selectableNetworks = new ArrayList<CyNetwork>();
		// Add empty network
		emptyNetwork.getRow(emptyNetwork).set(CyNetwork.NAME, "--");
		selectableNetworks.add( emptyNetwork );
		// Get possible independent flattened network
		Set<CyNetwork> allNetworks = ServiceProvider.get(CyNetworkManager.class).getNetworkSet();
		for (CyNetwork cyNetwork : allNetworks) {
			CyRow netRow = cyNetwork.getRow( cyNetwork );
			// network should not be involved into a multilayer network
			if( netRow.get(MlnWriter.IS_MLN, Boolean.class) == null
					|| ( netRow.get(MlnWriter.IS_MLN, Boolean.class) != null
							&& ! netRow.get(MlnWriter.IS_MLN, Boolean.class) ) )
				selectableNetworks.add( cyNetwork );
		}
		// Add networks to the tunable selection
		availableNetworks.setPossibleValues( selectableNetworks );
	}
	
	@Override
	public void run(TaskMonitor taskMonitor) throws MlnException {
		taskMonitor.setTitle("TimeNexus - Build MLN from flattened network");
		CyNetwork selectedNetwork = availableNetworks.getSelectedValue();
		// Check if there are selectable networks
		if( availableNetworks.getPossibleValues().size() == 1 )
			throw new MlnException("No available network.", "No available networks", JOptionPane.ERROR_MESSAGE);
		else if ( selectedNetwork.equals(emptyNetwork) )
			throw new MlnException("No network was selected.", "Empty network selection", JOptionPane.ERROR_MESSAGE);
		// Get the network
		taskMonitor.setStatusMessage("Converting the network to a flattened network...");
		CySubNetwork flatNetwork = (CySubNetwork) selectedNetwork;
		// Set the network as part of a MLN
		CyTable defaultNetTable = flatNetwork.getDefaultNetworkTable();
		CyRow netRow = defaultNetTable.getRow( flatNetwork.getSUID() ); 
		if ( netRow.get( MlnWriter.IS_MLN, Boolean.class ) == null )
			defaultNetTable.createColumn( MlnWriter.IS_MLN, Boolean.class, false );
		netRow.set( MlnWriter.IS_MLN, true );
		// Set the network as a flattened network
		CyTable localNetTable = flatNetwork.getTable( CyNetwork.class, CyNetwork.LOCAL_ATTRS );
		if ( netRow.get( MlnWriter.FLAT_NETWORK, Boolean.class ) == null )
			localNetTable.createColumn( MlnWriter.FLAT_NETWORK, Boolean.class, false ); 
		netRow.set( MlnWriter.FLAT_NETWORK, true );
		// Check format of the MLN
		taskMonitor.setStatusMessage("Checking format of the flattened network...");
		MlnReader.checkFlattenedNetworkFormat( flatNetwork );
		MlnReader.checkUniqueNodeNameForFlattenedNetwork( flatNetwork );
		// Set "Layer ID" columns as belonging to the local table of the flattened network
		copyLayerIdColFromSharedToLocal( flatNetwork, CyNode.class );
		copyLayerIdColFromSharedToLocal( flatNetwork, CyEdge.class );
		// Build multilayer network
		taskMonitor.setStatusMessage("Building a MLN from the flattened network...");
		CyTable nodeTable = flatNetwork.getDefaultNodeTable();
		List<Integer> selectedLayers = new ArrayList<Integer>( MlnReader.getLayerIdsFromFlattenedNetwork( nodeTable ) );
		MlnWriter.createMLNFromFlatNetwork( flatNetwork, selectedLayers );
	}
	
	/*
	 * Copy the column "Layer ID" from the shared table to the local table.
	 * @param flattened network with "Layer ID" in the shared table instead of the local table
	 * @param type of the table (CyNode or CyEdge)
	 */
	private void copyLayerIdColFromSharedToLocal( CyNetwork flatNetwork, Class<? extends CyIdentifiable> tableType ) {
		// Get the tables
		CyTable localTable = flatNetwork.getTable( tableType, CyNetwork.LOCAL_ATTRS );
		CyTable sharedTable = flatNetwork.getTable( tableType, CyRootNetwork.DEFAULT_ATTRS );
		// Get values of the column
		HashMap<Long, Integer> ids = new HashMap<Long, Integer>();
		for (CyRow row : sharedTable.getAllRows())
			ids.put( row.get( CyNetwork.SUID, Long.class ), row.get( MlnWriter.LAYER_ID, Integer.class ) );
		// Move the column from the shared to the local table
		sharedTable.deleteColumn( MlnWriter.LAYER_ID );
		localTable.createColumn( MlnWriter.LAYER_ID, Integer.class, false );
		// Add back the values
		List<CyRow> allRows = localTable.getAllRows(); 
		for ( CyRow row : allRows )
			row.set( MlnWriter.LAYER_ID, ids.get( row.get( CyNetwork.SUID, Long.class ) ) );
	}
	
}

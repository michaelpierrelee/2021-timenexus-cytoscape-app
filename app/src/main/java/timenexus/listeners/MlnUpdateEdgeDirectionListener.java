package timenexus.listeners;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkTableManager;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.events.RowsSetEvent;
import org.cytoscape.model.events.RowsSetListener;
import org.cytoscape.model.subnetwork.CyRootNetworkManager;

import timenexus.temporalnetwork.MlnBuilder;
import timenexus.temporalnetwork.MlnWriter;
import timenexus.utils.ServiceProvider;

/*
 * Update the internal edge direction (cyEdge.isDirected()) when the user change
 * the value of the column "Direction" of a flattened network (so-called the "official direction").
 * 
 * PathLinker uses the internal edge direction (cyEdge.isDirected()) to process networks.
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class MlnUpdateEdgeDirectionListener implements RowsSetListener {
	
	// (De)activate the listener
	private boolean activated = true; 
	// avoid to fire again this event after this listener updated a direction 
	private boolean fireNextEvent = true;

	public MlnUpdateEdgeDirectionListener() {}
	
	public void handleEvent(RowsSetEvent e) { if ( activated ) execute(e); }
	
	private void execute(RowsSetEvent e) {
		CyNetworkTableManager networkTableManager = ServiceProvider.get(CyNetworkTableManager.class); 
		CyRootNetworkManager rootNetManager = ServiceProvider.get(CyRootNetworkManager.class);
		
		//		Get the elements which fired the event
		// Table which fired the event
		CyTable firingTable = e.getSource();
		Class<? extends CyIdentifiable> networkObjectType = networkTableManager.getTableType( firingTable );
		// Network of the table
		CyNetwork network = null;
		CyTable networkTable = null;
		
		//		Test conditions
		boolean cond = false;
		if ( firingTable != null && networkObjectType != null ) {
			network = networkTableManager.getNetworkForTable( firingTable );
			networkTable = network.getDefaultNetworkTable();
			cond =
					// fired events from change of selections are ignored 
					e.getColumnRecords(CyNetwork.SELECTED).isEmpty() 
					// consider only the column that we are interesting in
					&& e.containsColumn(MlnBuilder.DIRECTION)
					// the event can come from a CyTableImpl or LocalFacadeTable, only one is considered
					&& firingTable.getClass().getSimpleName().equals("CyTableImpl")
					// the event comes from a CyEdge table
					&& networkObjectType.isAssignableFrom(CyEdge.class)
					// the event does not come from a root network
					&& network.getSUID() != rootNetManager.getRootNetwork(network).getSUID()
					// the network takes part in a multi-layer network collection 
					&& networkTable.getColumn( MlnWriter.IS_MLN ) != null
					&& networkTable.getRow( network.getSUID() ).get( MlnWriter.IS_MLN, Boolean.class )
					// the network has to be a flattened network
					&& networkTable.getColumn( MlnWriter.FLAT_NETWORK ) != null
					&& networkTable.getRow( network.getSUID() ).get( MlnWriter.FLAT_NETWORK, Boolean.class ) ;
		}
			
		//		Update the edge direction if the official direction was changed
		// Ignore the event as it comes from an update by itself
		if ( cond && ! fireNextEvent ) { fireNextEvent = true; }
		// Treat the event
		else if ( cond ) {
			int deactivate = JOptionPane.showConfirmDialog(new JFrame(),
					"The edge direction within a multi-layer network collection has been updated.\n"
					+ "Yet, the internal direction within Cytoscape has not. Thus, the update will be ignored by PathLinker.\n"
					+ "If you want to change it anyway, please create a new multi-layer network.\n\n"
					+ "Hide this message? Do not forget to reserve your current modifications.",
					"Warning: do not update the direction", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if ( deactivate == 0 ) activated = false;
			
			
			/*Print.out("--- event considered");
			// Go through each updated row
			for (RowSetRecord record: e.getPayloadCollection()) {
				// Get data
				String colName = record.getColumn();
				CyRow changedRow = record.getRow();
				CyEdge edge = network.getEdge( changedRow.get(CyNetwork.SUID, Long.class) );
				Boolean direction = (Boolean) record.getValue();
				Print.out( network.getRow(edge).getRaw(CyNetwork.NAME) + " - " + network.getRow(edge).getRaw(MlnBuilder.DIRECTION) + " - " + edge.isDirected() );
				// If the direction which was set is null, change it to FALSE
				if ( colName == MlnBuilder.DIRECTION && direction == null ) {
					changedRow.set( MlnBuilder.DIRECTION, false );
					direction = false;
					//fireNextEvent = false; // do not fire next event if the edge is not reset
				}
				// Change the internal direction according to the official direction
				if ( colName == MlnBuilder.DIRECTION && direction != edge.isDirected() ) {
					//TODO it should also update the layer networks and the inter-layer edge tables 
					edge = EdgeManagement.resetEdgeDirection( network, network.getDefaultEdgeTable(), edge, changedRow, direction );
					//fireNextEvent = true; // the edge was reset, so a next event can be fired
				}
			}*/
		}
	}

	public boolean isActivated() {
		return activated;
	}

	public void setActivated(boolean activated) {
		this.activated = activated;
	}
}

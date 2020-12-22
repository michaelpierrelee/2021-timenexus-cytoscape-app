package timenexus.temporalnetwork;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.session.CyNetworkNaming;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

import timenexus.temporalnetwork.MlnReader.MlnReaderException;
import timenexus.utils.Print;
import timenexus.utils.ServiceProvider;

/*
 * Make a copy of the flattened network with some layers asked by the user.
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class CopyFlattenedNetworkPanel extends AbstractTask {
	
	private JFrame frame;
	
	public CopyFlattenedNetworkPanel() {}

	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		// Get multi-layer networks
		List<CyRootNetwork> availableMlns = MlnReader.getMultiLayerNetworks();
		if ( availableMlns.isEmpty() )
			Print.messageDialog( "Multi-layer network not found",
					"No multi-layer network was found.\n"
					+ "A multi-layer network is expected to be a collection of networks with a column 'Multi-layer network'"
					+ " set as 'true' within the network tables.", JOptionPane.WARNING_MESSAGE );
		else
			createFrame( availableMlns );
	}
	
	/*
	 * Create the frame asking the elements to copy.
	 */
	private void createFrame( List<CyRootNetwork> availableMlns ) {
		frame = new JFrame( "Copy the layers from the flattened network" );
		frame.setMinimumSize(new Dimension(500, 250));
		frame.setLocationRelativeTo(null);
		// Select multilayer network
		JComboBox<CyRootNetwork> selectMlnCombo = new JComboBox<CyRootNetwork>();
			setComponentSize( selectMlnCombo, 200, selectMlnCombo.getMinimumSize().height );
			selectMlnCombo.addItem(null);
			availableMlns.stream().forEach( item -> { selectMlnCombo.addItem(item); } );
		Box selectMln = Box.createHorizontalBox();
			selectMln.add( new JLabel( "Select a multi-layer network: " ) );
			selectMln.add( selectMlnCombo );
		// Select layers to copy
		DefaultListModel<Integer> selectLayersModel = new DefaultListModel<Integer>();
		JList<Integer> selectLayersList = new JList<Integer>( selectLayersModel );
			selectLayersList.setSelectionMode( ListSelectionModel.SINGLE_INTERVAL_SELECTION );
			selectLayersList.setLayoutOrientation( JList.VERTICAL );
		JScrollPane scrollPane = new JScrollPane(selectLayersList);
			setComponentSize(scrollPane, 80, 150);
		Box selectLayers = Box.createHorizontalBox();
			selectLayers.add( new JLabel( "Select layers to copy: " ) );
			selectLayers.add( scrollPane );
		// Button
		JButton copyButton = new JButton( "Copy" );
		// Listeners
		selectMlnCombo.addActionListener( new UpdateLayersToCopyListener( selectMlnCombo, selectLayersModel ) );
		copyButton.addActionListener( new CopyLayersListener( selectMlnCombo, selectLayersList ) );
		// Set visible
		Box main = Box.createVerticalBox();
			main.add(selectMln);
			main.add(selectLayers);
			main.add(copyButton);
			main.add(Box.createGlue());
		frame.add(main);
		frame.setVisible(true);
	}
	
	/*
	 * Set component size
	 */
	private void setComponentSize( Component comp, int width, int height ) {
		comp.setMaximumSize( new Dimension( width, height ) );
		comp.setMinimumSize( new Dimension( width, height ) );
		comp.setPreferredSize( new Dimension( width, height ) );
	}
	
	/*_______________________________________
	 * 
	 *			LISTENERS
	 *_______________________________________
	 */
	
	/*
	 * Update the available layer IDs that the user can copy.
	 */
	private class UpdateLayersToCopyListener implements ActionListener{
		
		private JComboBox<CyRootNetwork> selectMlnCombo;
		private DefaultListModel<Integer> selectLayersModel;
		
		public UpdateLayersToCopyListener( JComboBox<CyRootNetwork> selectMlnCombo, DefaultListModel<Integer> selectLayersModel ) {
			this.selectMlnCombo = selectMlnCombo;
			this.selectLayersModel = selectLayersModel;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				// Get networks
				CyRootNetwork selectedMln = (CyRootNetwork) selectMlnCombo.getSelectedItem();
				if ( selectedMln != null ) {
					CySubNetwork flattenedNet = MlnReader.getMlnImageFromRoot( MlnWriter.FLAT_NETWORK, "flattened network", selectedMln );
					// Check format of the MLN
					MlnReader.checkFlattenedNetworkFormat(flattenedNet);
					MlnReader.checkUniqueNodeNameForFlattenedNetwork(flattenedNet);
					// Update list of layers
					selectLayersModel.removeAllElements();
					TreeSet<Integer> layers = MlnReader.getLayerIdsFromFlattenedNetwork( flattenedNet.getDefaultNodeTable() );
					IntStream.range(layers.first(), layers.last() + 1).boxed().forEach(
							item -> { selectLayersModel.addElement(item); }
							);
				} else
					selectLayersModel.removeAllElements();
			} catch( MlnReaderException err ) {
				Print.error(err);
			}
		}
		
	}
	
	/*
	 * Copy the selected layers from the flattened network into a new network.
	 */
	private class CopyLayersListener implements ActionListener{
		
		private JComboBox<CyRootNetwork> selectMlnCombo;
		private JList<Integer> selectLayersList;
		
		public CopyLayersListener( JComboBox<CyRootNetwork> selectMlnCombo, JList<Integer> selectLayersList ) {
			this.selectMlnCombo = selectMlnCombo;
			this.selectLayersList = selectLayersList;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				// Get networks
				CyRootNetwork selectedMln = (CyRootNetwork) selectMlnCombo.getSelectedItem();
				if ( selectedMln != null ) {
					CySubNetwork flattenedNet = MlnReader.getMlnImageFromRoot( MlnWriter.FLAT_NETWORK, "flattened network", selectedMln );
					// Check format of the MLN
					MlnReader.checkFlattenedNetworkFormat(flattenedNet);
					MlnReader.checkUniqueNodeNameForFlattenedNetwork(flattenedNet);
					// Copy layers
					List<Integer> selectedLayers = selectLayersList.getSelectedValuesList();
					try {
						// Get list of node names
						Set<String> allNodes = new HashSet<String>();
						for ( CyNode node : flattenedNet.getNodeList() )
							allNodes.add( flattenedNet.getRow( node ).get( CyNetwork.NAME, String.class ) );
						//		Create the MLN
						CyNetwork copiedFlatNet = copyLayers( flattenedNet, selectedLayers, allNodes, "Copied multi-layer network" );
						ServiceProvider.get(CyNetworkManager.class).addNetwork( copiedFlatNet );
						MlnWriter.createMLNFromFlatNetwork(copiedFlatNet, selectedLayers);
						// Do another?
						int another = JOptionPane.showConfirmDialog(frame,
								"The flattened network has been generated. Close?",
								"Successful copy", JOptionPane.YES_NO_OPTION);
						if( another == 0 ) frame.dispose();
					} catch (Exception e1) {
						Print.messageDialog(
								"The flattened network was not copied.\n\n"
								+ "Error: " + e1.getClass().getName() + "\n" + e1.getStackTrace()[0],
								"Copy failed", JOptionPane.ERROR_MESSAGE);
						Print.error(e1);
					}
				}
			} catch( MlnReaderException err ) {
				Print.error(err);
			}
		}
		
	}
	
	/*_______________________________________
	 * 
	 *			PROCESSING
	 *_______________________________________
	 */

	/*
	 * Copy the selected set of layers from the flattened network.
	 * The resulting network is so a subset of the flattened network.
	 * @param flattened network to copy
	 * @param selectedLayers: list of the layer IDs that the user wants to get.
	 * @param set of nodes to copy from the flattened network
	 * @param name of the copied network
	 * @return the copied flattened network
	 */
	public static CyNetwork copyLayers( CySubNetwork flattenedNet,
			List<Integer> selectedLayers, Set<String> nodesToCopy, String networkName ) {
		//		Create the network
		CyNetworkFactory networkFactory = ServiceProvider.get(CyNetworkFactory.class);
		CyNetwork copiedNet = networkFactory.createNetwork();
		// Set name of the network
		CyNetworkNaming serviceNetworkNaming = ServiceProvider.get(CyNetworkNaming.class);
		String rootName = serviceNetworkNaming.getSuggestedNetworkTitle( networkName );
		CyTable netTable = copiedNet.getDefaultNetworkTable();
		netTable.getRow ( copiedNet.getSUID() ).set( "name", rootName );
		// Add MLN and flattened network columns to the network table
		netTable.createColumn( MlnWriter.IS_MLN, Boolean.class, false );
		netTable.getRow( copiedNet.getSUID() ).set( MlnWriter.IS_MLN, true );
		CyTable localNetTable = copiedNet.getTable(CyNetwork.class, CyNetwork.LOCAL_ATTRS);
		localNetTable.createColumn( MlnWriter.FLAT_NETWORK, Boolean.class, false );
		localNetTable.getRow( copiedNet.getSUID() ).set( MlnWriter.FLAT_NETWORK, true );
		
		//		Copy the columns
		CyTable flatNodeTable = flattenedNet.getDefaultNodeTable();
		CyTable flatEdgeTable = flattenedNet.getDefaultEdgeTable();
		CyTable copiedNodeTable = copiedNet.getDefaultNodeTable();
		CyTable copiedEdgeTable = copiedNet.getDefaultEdgeTable();
		copyColumns( flatNodeTable, copiedNodeTable );
		copyColumns( flatEdgeTable, copiedEdgeTable );
		copiedNet.getTable(CyNode.class, CyNetwork.LOCAL_ATTRS).createColumn(MlnWriter.LAYER_ID, Integer.class, false);
		copiedNet.getTable(CyEdge.class, CyNetwork.LOCAL_ATTRS).createColumn(MlnWriter.LAYER_ID, Integer.class, false);
		copiedNet.getTable(CyEdge.class, CyNetwork.LOCAL_ATTRS).createColumn(MlnWriter.EDGE_LABEL, String.class, false);
		
		//		Copy the elements
		// Nodes
		HashMap<String, CyNode> addedNodes = new HashMap<String, CyNode>();
		for ( CyRow flatRow : flatNodeTable.getAllRows() ) {
			int layerID = flatRow.get(MlnWriter.LAYER_ID, Integer.class);
			String nodeName = flatRow.get(CyNetwork.NAME, String.class);
			if ( selectedLayers.contains( layerID ) && nodesToCopy.contains(nodeName) ) {
				CyNode copiedNode = copiedNet.addNode();
				CyRow copiedRow = copiedNodeTable.getRow( copiedNode.getSUID() );
				copyCells( flatRow, copiedRow, flatNodeTable, copiedNodeTable );
				addedNodes.put( nodeName, copiedNode );
			}
		}
		// Edges
		for ( CyEdge flatEdge : flattenedNet.getEdgeList() ) {
			CyRow flatRow = flatEdgeTable.getRow( flatEdge.getSUID() );
			String edgeType = flatRow.get(MlnWriter.EDGE_LABEL, String.class);
			int layerID = flatRow.get(MlnWriter.LAYER_ID, Integer.class);
			if (
					// for intra-layer edges, the edge should be part of one of the selected layers
					( edgeType.equals("intra-layer") && selectedLayers.contains( layerID ) ) 
					// for inter-layer edges, both source and target nodes should be coming from the selected layers
					|| ( edgeType.equals("inter-layer") && selectedLayers.contains( layerID ) && selectedLayers.contains( layerID + 1 ) )
					) {
				// Get nodes of the edge
				CyRow flatSourceRow = flatNodeTable.getRow( flatEdge.getSource().getSUID() );
				CyRow flatTargetRow = flatNodeTable.getRow( flatEdge.getTarget().getSUID() );
				String sourceName = flatSourceRow.get( CyNetwork.NAME, String.class );
				String targetName = flatTargetRow.get( CyNetwork.NAME, String.class );
				// Create edge if sources and targets where copied
				if ( nodesToCopy.contains(sourceName) && nodesToCopy.contains(targetName) ) {
					CyNode copiedSource = addedNodes.get( sourceName );
					CyNode copiedTarget = addedNodes.get( targetName );
					CyEdge copiedEdge = copiedNet.addEdge( copiedSource, copiedTarget, flatEdge.isDirected() );
					CyRow copiedRow = copiedEdgeTable.getRow( copiedEdge.getSUID() );
					copyCells( flatRow, copiedRow, flatEdgeTable, copiedEdgeTable );
				}
				
				
			}
		}
		
		return copiedNet;
	}
	
	/*
	 * Copy columns of the CyTable tableFrom to the CyTable tableTo.
	 */
	private static void copyColumns( CyTable tableFrom, CyTable tableTo ) {
		for ( CyColumn col : tableFrom.getColumns() ) {
			String colName = col.getName();
			Class<?> colType = col.getType();
			// Copy all columns to the table, except LAYER_ID and EDGE_LABEL
			if( tableTo.getColumn(colName) == null
					&& ! colName.equals(MlnWriter.LAYER_ID)
					&& ! colName.equals(MlnWriter.EDGE_LABEL) ) {
				// copy the column with standard values
				if ( col.getListElementType() == null ) tableTo.createColumn( colName, colType, false );
				// copy the list column
				else tableTo.createListColumn( colName, col.getListElementType(), false );;
			}
				
			
		}
	}
	
	/*
	 * Copy cells of the CyRow rowFrom to the CyRow rowTo.
	 */
	private static void copyCells( CyRow rowFrom, CyRow rowTo, CyTable tableFrom, CyTable tableTo ) {
		for ( CyColumn col : tableTo.getColumns() ) {
			String colName = col.getName();
			Class<?> colType = col.getType();
			rowTo.set( colName, rowFrom.get( colName, colType ) );
		}
	}
	
}

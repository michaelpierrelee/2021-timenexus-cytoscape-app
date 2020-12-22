package timenexus.view;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;

import timenexus.temporalnetwork.MlnReader;
import timenexus.temporalnetwork.MlnWriter;
import timenexus.temporalnetwork.MlnReader.MlnReaderException;
import timenexus.utils.MlnException;
import timenexus.utils.Print;
import timenexus.utils.ServiceProvider;

/*
 * Create a CytoPanel to make a view for the flattened network of the multi-layer network.
 * 
 * It merely takes the layout of the aggregated network and it applies it to each layer of
 * the flattened network.
 * 
 * The user can select which layers to display by selecting one layer to focus and then,
 * by choosing the number of adjacent layers. 
 * 
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class TimeNexusViewerPanel extends JScrollPane implements CytoPanelComponent {

	private static final long serialVersionUID = 1L;
	
	// ComboBox with the multi-layer networks that could be loaded
	private JComboBox<CyRootNetwork> selectMlnFlatCombo = new JComboBox<CyRootNetwork>();
	private JComboBox<CyRootNetwork> selectMlnAggCombo = new JComboBox<CyRootNetwork>();
	
	/*
	 * Create the panel.
	 */
	public TimeNexusViewerPanel() {
		/**** Define form elements ****/
		
		// Load available multi-layer networks
		JButton loadMlnsButton = new JButton( "Load multi-layer networks:" );
		Box loadMlnsBox = Box.createHorizontalBox();
			loadMlnsBox.add( loadMlnsButton );
		// Select multi-layer network of the flattened network
		Box selectMlnFlatLabel = Box.createHorizontalBox();
			selectMlnFlatLabel.add(new JLabel( "Select the multi-layer network for the flattened network:" ));
			selectMlnFlatLabel.add(Box.createHorizontalGlue());
		setComponentSize( selectMlnFlatCombo, 300, selectMlnFlatCombo.getMinimumSize().height );
		// Select flattened network
		Box selectFlatNetLabel = Box.createHorizontalBox();
			selectFlatNetLabel.add(new JLabel( "Name of flattened network:" ));
			selectFlatNetLabel.add(Box.createHorizontalGlue());
		JTextField selectFlatNetText = new JTextField();
			selectFlatNetText.setEditable(false);
			setComponentSize( selectFlatNetText, 300, selectFlatNetText.getMinimumSize().height );
		// Select multi-layer network of the aggregated network
		Box selectMlnAggLabel = Box.createHorizontalBox();
			selectMlnAggLabel.add(new JLabel( "Select the multi-layer network for the aggregated network:" ));
			selectMlnAggLabel.add(Box.createHorizontalGlue());
		setComponentSize( selectMlnAggCombo, 300, selectMlnAggCombo.getMinimumSize().height );
		// Select aggregated network
		Box selectAggNetLabel = Box.createHorizontalBox();
			selectAggNetLabel.add(new JLabel( "Name of the aggregated network:" ));
			selectAggNetLabel.add(Box.createHorizontalGlue());
		JTextField selectAggNetText = new JTextField();
			selectAggNetText.setEditable(false);
			setComponentSize( selectAggNetText, 300, selectAggNetText.getMinimumSize().height );
		// Select layer to focus
		JLabel selectFocusLayerLabel = new JLabel( "Select the layer to focus: " );
		JSlider selectFocusLayerSlider = new JSlider( JSlider.HORIZONTAL, 0, 0, 0 );
			selectFocusLayerSlider.setMajorTickSpacing(1);
			selectFocusLayerSlider.setMinorTickSpacing(1);
			selectFocusLayerSlider.setPaintTicks(true);
			selectFocusLayerSlider.setPaintLabels(true);
			selectFocusLayerSlider.setSnapToTicks(true);
		Box selectFocusLayerRow = defineFormRows( selectFocusLayerLabel, selectFocusLayerSlider );
		// Select number of adjacent layers
		JLabel selectAdjLayersLabel = new JLabel( "Number of adjacent layers per side: " );
		JSpinner selectAdjLayersSpinner = new JSpinner();
		Box selectAdjLayersRow = defineFormRows( selectAdjLayersLabel, selectAdjLayersSpinner );
		// Select adjacent layers to show
		JLabel showAdjLayersLabel = new JLabel( "Show adjacent layers: " );
		JComboBox<String> showAdjLayersCombo = new JComboBox<String>();
			showAdjLayersCombo.addItem( "forward" );
			showAdjLayersCombo.addItem( "backward" );
			showAdjLayersCombo.addItem( "on both sides" );
		Box showAdjLayersRow = defineFormRows( showAdjLayersLabel, showAdjLayersCombo );
		// Update layout of flattened network
		JButton updateLayoutButton = new JButton( "Update the view of the flattened network" );
		Box updateLayoutBox = Box.createHorizontalBox();
			updateLayoutBox.add( updateLayoutButton );
			
		/**** Add listeners ****/
		loadMlnsButton.addActionListener( new LoadMlnsListener() );
		selectMlnFlatCombo.addItemListener( new UpdateFlattenedNetworkListener( selectFlatNetText,
				selectFocusLayerSlider, selectAdjLayersSpinner, showAdjLayersCombo ) );
		selectMlnAggCombo.addItemListener( new UpdateAggregatedNetworkListener( selectAggNetText ) );
		updateLayoutButton.addActionListener( new UpdateViewListener( selectFocusLayerSlider, selectAdjLayersSpinner, showAdjLayersCombo ) );
		
		/**** Define groups ****/
		
		// Select networks
		Box selectNetworks = Box.createVerticalBox();
		selectNetworks.setBorder( BorderFactory.createTitledBorder( "1. Select the networks" ) );
		selectNetworks.add(loadMlnsBox);
		selectNetworks.add(selectMlnFlatLabel);
		selectNetworks.add(selectMlnFlatCombo);
		selectNetworks.add(selectFlatNetLabel);
		selectNetworks.add(selectFlatNetText);
		selectNetworks.add(selectMlnAggLabel);
		selectNetworks.add(selectMlnAggCombo);
		selectNetworks.add(selectAggNetLabel);
		selectNetworks.add(selectAggNetText);
		// Select layers
		Box selectLayers = Box.createVerticalBox();
		selectLayers.setBorder( BorderFactory.createTitledBorder( "2. Select the layers" ) );
		selectLayers.add(selectFocusLayerRow);
		selectLayers.add(selectAdjLayersRow);
		selectLayers.add(showAdjLayersRow);
		selectLayers.add(updateLayoutBox);
		
		/**** Display ****/
		Box mainPanel = Box.createVerticalBox();
		mainPanel.add(Box.createVerticalGlue());
		mainPanel.add(selectNetworks);
		mainPanel.add(selectLayers);
		mainPanel.add(Box.createVerticalGlue());
		setViewportView( mainPanel );
	}
	
	/*_______________________________________
	 * 
	 *			UPDATE NETWORKS
	 *_______________________________________
	 */
	
	/*
	 * Get selected multi-layer network.
	 */
	private CyRootNetwork getSelectMln( JComboBox<CyRootNetwork> selectMlnCombo ) {
		return selectMlnCombo.getItemAt( selectMlnCombo.getSelectedIndex() );
	}
	
	/*
	 * Get flattened network
	 */
	private CySubNetwork getFlattenedNetwork( JComboBox<CyRootNetwork> selectMlnCombo ) throws MlnReaderException {
		CyRootNetwork mln = getSelectMln( selectMlnCombo );
		return MlnReader.getMlnImageFromRoot( MlnWriter.FLAT_NETWORK, "flattened network", mln );
	}
	
	/*
	 * Get aggregated network.
	 */
	private CySubNetwork getAggregatedNetwork( JComboBox<CyRootNetwork> selectMlnCombo ) throws MlnReaderException {
		CyRootNetwork mln = getSelectMln( selectMlnCombo );
		return MlnReader.getMlnImageFromRoot( MlnWriter.AGG_NETWORK, "aggregated network", mln );
	}
	
	/*
	 * Update flattened networks.
	 */
	private void updateFlattenedNetwork( JComboBox<CyRootNetwork> selectMlnCombo, JTextField flatNetworkText ) throws MlnReaderException {
		CySubNetwork flattenedNet = getFlattenedNetwork( selectMlnCombo );
		flatNetworkText.setText( flattenedNet.getDefaultNetworkTable()
				.getRow( flattenedNet.getSUID() )
				.get(CyNetwork.NAME, String.class)  );
	}
	
	/*
	 * Update aggregated networks.
	 */
	private void updateAggregatedNetwork( JComboBox<CyRootNetwork> selectMlnCombo, JTextField aggNetworkText ) throws MlnReaderException {
		CySubNetwork aggregatedNet = getAggregatedNetwork( selectMlnCombo );
		aggNetworkText.setText( aggregatedNet.getDefaultNetworkTable()
				.getRow( aggregatedNet.getSUID() )
				.get(CyNetwork.NAME, String.class)  );
	}
	
	private void updateLayoutParameters( int nbLayers,
			JSlider focusLayerSlider, JSpinner adjLayerSpinner, JComboBox<String> showAdjLayersCombo ) {
		// Update the slider
		int min = 1, max = nbLayers - 1;
		if ( nbLayers <= 1 ) {
			min = nbLayers;
			max = nbLayers;
		}
		focusLayerSlider.setMinimum(min);
		focusLayerSlider.setMaximum(nbLayers);
		// Update number of adjacent layers
		adjLayerSpinner.setModel( new SpinnerNumberModel(0, 0, max, 1) );
	}
	
	/*_______________________________________
	 * 
	 *			UTILS
	 *_______________________________________
	 */
	
	/*
	 * Define rows of the form
	 */
	private Box defineFormRows( JLabel label, Component comp ) {
		Box box = Box.createHorizontalBox();
		box.add(label);
		box.add(comp);
		box.add(Box.createGlue());
		setComponentSize( box, 400, box.getMinimumSize().height );
		return box;
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
	 * Update multi-layer networks.
	 */
	private class LoadMlnsListener implements ActionListener{

		public LoadMlnsListener() {}
		
		@Override
		public void actionPerformed(ActionEvent e) {
			selectMlnFlatCombo.removeAllItems();
			selectMlnAggCombo.removeAllItems();
			for (CyRootNetwork net : MlnReader.getMultiLayerNetworks()) {
				selectMlnFlatCombo.addItem(net);
				selectMlnAggCombo.addItem(net);
				}
		}
		
	}
	
	/*
	 * Update fields of the flattened network when the multi-layer network for the flattened network is updated.
	 */
	private class UpdateFlattenedNetworkListener implements ItemListener {
		
		JTextField flatNetworkText;
		JSlider focusLayerSlider;
		JSpinner adjLayerSpinner;
		JComboBox<String> showAdjLayersCombo;
		
		public UpdateFlattenedNetworkListener( JTextField flatNetworkText,
				JSlider focusLayerSlider, JSpinner adjLayerSpinner, JComboBox<String> showAdjLayersCombo ) {
			this.flatNetworkText = flatNetworkText;
			this.focusLayerSlider = focusLayerSlider;
			this.adjLayerSpinner = adjLayerSpinner;
			this.showAdjLayersCombo = showAdjLayersCombo;
		}
		
		@Override
		public void itemStateChanged(ItemEvent e) {
			if ( selectMlnFlatCombo.getItemCount() > 0 ) {
				try {
					CySubNetwork flattenedNet = getFlattenedNetwork( selectMlnFlatCombo );
					// Check format of the MLN
					MlnReader.checkFlattenedNetworkFormat(flattenedNet);
					MlnReader.checkUniqueNodeNameForFlattenedNetwork(flattenedNet);
					// Update the networks
					updateFlattenedNetwork( selectMlnFlatCombo, flatNetworkText );
					// Update layout parameters
					int nbLayers = MlnReader.getNbOfLayersFromFlattenedNetwork( flattenedNet );
					updateLayoutParameters( nbLayers, focusLayerSlider, adjLayerSpinner, showAdjLayersCombo );
				} catch (MlnReaderException err) {
					Print.error(err);
					Print.messageDialog( err.getMessageTitle(), err.getMessage(), err.getMessageType() );
				}	
			}
		}
	}
	
	/*
	 * Update fields of the aggregated network when the multi-layer network for the aggregated network is updated.
	 */
	private class UpdateAggregatedNetworkListener implements ItemListener {
		
		JTextField aggNetworkText;
		
		public UpdateAggregatedNetworkListener( JTextField aggNetworkText ) {
			this.aggNetworkText = aggNetworkText;
		}
		
		@Override
		public void itemStateChanged(ItemEvent e) {
			if ( selectMlnFlatCombo.getItemCount() > 0 ) {
				try {
					CySubNetwork aggregatedNet = getAggregatedNetwork( selectMlnAggCombo );
					// Check format of the MLN
					MlnReader.checkAggregatedNetworkFormat(aggregatedNet);
					MlnReader.checkUniqueNodeNameForAggregatedNetwork(aggregatedNet);
					// Update the networks
					updateAggregatedNetwork( selectMlnAggCombo, aggNetworkText );
				} catch (MlnReaderException err) {
					Print.error(err);
					Print.messageDialog( err.getMessageTitle(), err.getMessage(), err.getMessageType() );
				}	
			}
		}
	}
	
	/*
	 * Update the view of the flattened network by loading view of the aggregated network.
	 */
	private class UpdateViewListener implements ActionListener {
		
		JSlider focusLayerSlider;
		JSpinner adjLayerSpinner;
		JComboBox<String> showAdjLayersCombo;
		
		public UpdateViewListener( JSlider focusLayerSlider, JSpinner adjLayerSpinner, JComboBox<String> showAdjLayersCombo ) {
			this.focusLayerSlider = focusLayerSlider;
			this.adjLayerSpinner = adjLayerSpinner;
			this.showAdjLayersCombo = showAdjLayersCombo;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				// Get networks
				CySubNetwork flattenedNet = getFlattenedNetwork( selectMlnFlatCombo );
				CySubNetwork aggregatedNet = getAggregatedNetwork( selectMlnAggCombo );
				// Get layers to display
				int focusLayer = focusLayerSlider.getValue();
				int adjLayer = (int) adjLayerSpinner.getValue();
				String showAdjLayers = (String) showAdjLayersCombo.getSelectedItem();
				List<Integer> layersToShow = new ArrayList<Integer>();
				layersToShow.add(focusLayer);
				if ( showAdjLayers.equals("forward") )
					for (int i = 1; i <= adjLayer; i++) layersToShow.add( focusLayer + i );
				else if ( showAdjLayers.equals("backward") )
					for (int i = 1; i <= adjLayer; i++) layersToShow.add( focusLayer - i );
				else
					for (int i = 1; i <= adjLayer; i++) {
						layersToShow.add( focusLayer + i );
						layersToShow.add( focusLayer - i );
					}
				// Create view
				TaskIterator flatViewerTaskItr = new TaskIterator(
						new MlnFlatViewerTask( flattenedNet, aggregatedNet, layersToShow )
						);
				
				ServiceProvider.get(TaskManager.class).execute( flatViewerTaskItr );
			} catch ( MlnException err ) {
				Print.error(err);
				Print.messageDialog( err.getMessageTitle(), err.getMessage(), err.getMessageType() );
			}
		}
	}
		
	/*_______________________________________
	 * 
	 *			MISCELLEANOUS
	 *_______________________________________
	 */

	public Component getComponent() {
		return this;
	}


	public CytoPanelName getCytoPanelName() {
		return CytoPanelName.WEST;
	}


	public String getTitle() {
		return "TimeNexus Viewer";
	}


	public Icon getIcon() {
		return null;
	}

}

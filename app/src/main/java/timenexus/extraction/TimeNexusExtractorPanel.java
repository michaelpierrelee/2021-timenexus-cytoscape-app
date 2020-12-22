package timenexus.extraction;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.lang.reflect.InvocationTargetException;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.IntStream;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;

import timenexus.apps.AnatCaller;
import timenexus.apps.AppCaller;
import timenexus.apps.PathlinkerCaller;
import timenexus.temporalnetwork.MlnReader;
import timenexus.temporalnetwork.MlnWriter;
import timenexus.temporalnetwork.MlnReader.MlnReaderException;
import timenexus.utils.Print;
import timenexus.utils.ServiceProvider;

/*
 * Create a panel to run a subnetwork extraction on a multi-layer network.
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class TimeNexusExtractorPanel extends JScrollPane implements CytoPanelComponent {

	private static final long serialVersionUID = 1L;
	
	// ComboBox with the multi-layer networks that could be loaded
	private JComboBox<CyRootNetwork> selectMlnCombo = new JComboBox<CyRootNetwork>();
	// Extracting app to be used
	private AppCaller app;
	// Extraction method to be applied on the extracting app
	private ExtractionMethod method;
	
	Box mainPanel = Box.createVerticalBox();

	/*
	 * Create the panel.
	 */
	public TimeNexusExtractorPanel() {
		/**** Define form elements ****/
		
		// Load available multi-layer networks
		JButton loadMlnsButton = new JButton( "Load multi-layer networks" );
		Box loadMlnsBox = Box.createHorizontalBox();
			loadMlnsBox.add( loadMlnsButton );
		// Select multi-layer network
		Box selectMlnLabel = Box.createHorizontalBox();
			selectMlnLabel.add( new JLabel( "Select multi-layer network:" ) );
			selectMlnLabel.add( Box.createHorizontalGlue() );
		setComponentSize( selectMlnCombo, 300, selectMlnCombo.getMinimumSize().height );
		// Select flattened network
		Box selectFlatNetLabel = Box.createHorizontalBox();
			selectFlatNetLabel.add( new JLabel( "Name of Flattened network:" ) );
			selectFlatNetLabel.add( Box.createHorizontalGlue() );
		JTextField selectFlatNetText = new JTextField();
			selectFlatNetText.setEditable(false);
			setComponentSize( selectFlatNetText, 300, selectFlatNetText.getMinimumSize().height );
		// Select layers to use
		Box selectLayersLabel = Box.createHorizontalBox();
			selectLayersLabel.add( new JLabel( "Select layers to use: " ) );
			selectLayersLabel.add( Box.createHorizontalGlue() );
		DefaultListModel<Integer> selectLayersModel = new DefaultListModel<Integer>();
		JList<Integer> selectLayersList = new JList<Integer>( selectLayersModel );
			selectLayersList.setSelectionMode( ListSelectionModel.SINGLE_INTERVAL_SELECTION );
			selectLayersList.setLayoutOrientation( JList.VERTICAL );
		JScrollPane selectLayersPane = new JScrollPane(selectLayersList);
			selectLayersPane.setToolTipText("Select layers to include to the extraction. "
					+ "If only one is selected, the applied method will be 'One by one'.");
			setComponentSize(selectLayersPane, 80, 10);
		// Enable check of the MLN
		JCheckBox enableCheckMLNCheck = new JCheckBox();
			enableCheckMLNCheck.setSelected(true);
		Box enableCheckMLNBox = Box.createHorizontalBox();
			enableCheckMLNBox.add( new JLabel( "Verify the multi-layer network: " ) );
			enableCheckMLNBox.add( enableCheckMLNCheck );
			enableCheckMLNBox.add( Box.createHorizontalGlue() );
			enableCheckMLNBox.setToolTipText( "If uncheck, speed up the job byt TimeNexus will not verify whether "
					+ "the multi-layer network has a valid format for the extracting app." );
		// Select method to use
		JRadioButton globalMethod = new JRadioButton( "Global" );
			globalMethod.setActionCommand( globalMethod.getText() );
			globalMethod.setToolTipText("Extraction is performed on the full flattened multi-layer network. "
					+ "The source and target nodes will come from the first layer and the last layer, respectively.");
		JRadioButton pairwiseMethod = new JRadioButton( "Pairwise" );
			pairwiseMethod.setActionCommand( pairwiseMethod.getText() );
			pairwiseMethod.setToolTipText("Extraction is performed on each successive pair of layers. "
					+ "For N-th extraction, the source and target nodes will come from the layer N and N+1, respectively.");
		JRadioButton oneByOneMethod = new JRadioButton( "One By One" );
			oneByOneMethod.setActionCommand( oneByOneMethod.getText() );
			oneByOneMethod.setToolTipText("Extraction is independently performed on each layer. "
					+ "The source nodes will be the same as the target nodes.");
		ButtonGroup methodRadioGroup = new ButtonGroup();
			methodRadioGroup.add(globalMethod);
			methodRadioGroup.add(pairwiseMethod);
			methodRadioGroup.add(oneByOneMethod);
			setEnabled(methodRadioGroup, false);
		// Select app to use
		JRadioButton pathlinkerApp = new JRadioButton( "PathLinker" );
			pathlinkerApp.setActionCommand( pathlinkerApp.getText() );
		/*JRadioButton viperApp = new JRadioButton( "viPEr" );
			viperApp.setActionCommand( viperApp.getText() );*/
		JRadioButton anatApp = new JRadioButton( "AnatApp" );
			anatApp.setActionCommand( anatApp.getText() );
		ButtonGroup appRadioGroup = new ButtonGroup();
			appRadioGroup.add(anatApp);	
			appRadioGroup.add(pathlinkerApp);
			//appRadioGroup.add(viperApp);
			setEnabled(appRadioGroup, false);
		// Extract
		JButton extractButton = new JButton("Extract subnetworks");
			extractButton.setEnabled(false);
		Box extractButtonBox = Box.createHorizontalBox();
			extractButtonBox.add( extractButton );

		/**** Define groups ****/
		// Select the network
		Box selectNetworks = Box.createVerticalBox();
			selectNetworks.setBorder( BorderFactory.createTitledBorder( "1. Select the multi-layer network" ) );
			selectNetworks.add(loadMlnsBox);
			selectNetworks.add(selectMlnLabel);
			selectNetworks.add(selectMlnCombo);
			selectNetworks.add(selectFlatNetLabel);
			selectNetworks.add(selectFlatNetText);
			selectNetworks.add(selectLayersLabel);
			selectNetworks.add(selectLayersPane);
			selectNetworks.add(enableCheckMLNBox);
			selectNetworks.add( Box.createHorizontalGlue() );
			selectNetworks.setAlignmentX( Component.LEFT_ALIGNMENT );
		// Select extracting method
		Box selectMethod = Box.createHorizontalBox();
			selectMethod.setBorder( BorderFactory.createTitledBorder( "2. Select the extracting method" ) );
			selectMethod.add( Box.createHorizontalGlue() );
			selectMethod.add(globalMethod);
			selectMethod.add( Box.createHorizontalGlue() );
			selectMethod.add(pairwiseMethod);
			selectMethod.add( Box.createHorizontalGlue() );
			selectMethod.add(oneByOneMethod);
			selectMethod.add( Box.createHorizontalGlue() );
			selectMethod.setAlignmentX( Component.LEFT_ALIGNMENT );
		// Select extracting app to call
		Box selectApp = Box.createHorizontalBox();
			selectApp.setBorder( BorderFactory.createTitledBorder( "3. Select the extracting app" ) );
			selectApp.add( Box.createHorizontalGlue() );
			selectApp.add( pathlinkerApp );
			selectApp.add( Box.createHorizontalGlue() );
			//selectApp.add( viperApp );
			//selectApp.add( Box.createHorizontalGlue() );
			selectApp.add( anatApp );
			selectApp.add( Box.createHorizontalGlue() );
			selectApp.setAlignmentX( Component.LEFT_ALIGNMENT );
		// Select app parameters
		Box paramGroup = Box.createVerticalBox();
			paramGroup.add(new JLabel("Select an extracting app"));
			//paramGroup.add( Box.createHorizontalGlue() );
		Box selectParams = Box.createVerticalBox();
			selectParams.setBorder( BorderFactory.createTitledBorder( "4. Select parameters of the app" ) );
			selectParams.add( paramGroup );
			selectParams.add( Box.createHorizontalGlue() );
			selectParams.setAlignmentX( Component.LEFT_ALIGNMENT );
		// Extract button
		Box buttonGroup = Box.createVerticalBox();
			buttonGroup.add( extractButtonBox );
			buttonGroup.add( Box.createHorizontalGlue() );
			buttonGroup.setAlignmentX( Component.LEFT_ALIGNMENT );

		/**** Add listeners ****/
			LoadMlnsListener loadMlnsListener = new LoadMlnsListener( appRadioGroup, methodRadioGroup, selectLayersList, paramGroup, extractButton );
			loadMlnsButton.addActionListener( loadMlnsListener );
			globalMethod.addActionListener( new SetExtractionMethod( methodRadioGroup, appRadioGroup ) );
			pairwiseMethod.addActionListener( new SetExtractionMethod( methodRadioGroup, appRadioGroup ) );;
			oneByOneMethod.addActionListener( new SetExtractionMethod( methodRadioGroup, appRadioGroup ) );
			selectMlnCombo.addItemListener( new UpdateFlattenNetwork( selectFlatNetText,
					selectLayersModel, selectLayersList, selectLayersPane ) );
			pathlinkerApp.addActionListener( new AddParamToFormListener( paramGroup, selectLayersList, extractButton,
					PathlinkerCaller.class ) );
			anatApp.addActionListener( new AddParamToFormListener( paramGroup, selectLayersList, extractButton,
					AnatCaller.class ) );
			extractButton.addActionListener( new RunExtractionListener( selectLayersList, enableCheckMLNCheck ) );
			
		/**** Display ****/
		
			mainPanel.add(Box.createVerticalGlue());
			mainPanel.add(selectNetworks);
			mainPanel.add(selectMethod);
			mainPanel.add(selectApp);
			mainPanel.add(selectParams);
			mainPanel.add(buttonGroup);
			mainPanel.add(Box.createVerticalGlue());
		setViewportView( mainPanel );
	}
	
	/*_______________________________________
	 * 
	 *			GET NETWORKS
	 *_______________________________________
	 */
	
	/*
	 * Get selected multi-layer network.
	 */
	private CyRootNetwork getSelectMln() {
		return selectMlnCombo.getItemAt( selectMlnCombo.getSelectedIndex() );
	}
	
	/*
	 * Get flattened network
	 */
	private CySubNetwork getFlattenedNetwork() throws MlnReaderException {
		CyRootNetwork mln = getSelectMln();
		return MlnReader.getMlnImageFromRoot( MlnWriter.FLAT_NETWORK, "flattened network", mln );
	}
	
	/*_______________________________________
	 * 
	 *			UTILS
	 *_______________________________________
	 */
	
	/*
	 * Enable or disable buttons of button group
	 */
	private void setEnabled(ButtonGroup radioGroup, boolean enable) {
		Enumeration<AbstractButton> enumeration = radioGroup.getElements();
		while (enumeration.hasMoreElements()) {
		    enumeration.nextElement().setEnabled(enable);
		}
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
	 * Update available multi-layer networks.
	 */
	private class LoadMlnsListener implements ActionListener{
		
		ButtonGroup appRadioGroup;
		ButtonGroup methodRadioGroup;
		JList<Integer> selectLayersList;
		Box paramGroup;
		JButton extractButton;
		
		public LoadMlnsListener( ButtonGroup appRadioGroup, ButtonGroup methodRadioGroup,
				JList<Integer> selectLayersList, Box paramGroup, JButton extractButton ) {
			this.appRadioGroup = appRadioGroup;
			this.methodRadioGroup = methodRadioGroup;
			this.selectLayersList = selectLayersList;
			this.paramGroup = paramGroup;
			this.extractButton = extractButton;
		}
		
		@Override
		public void actionPerformed(ActionEvent e) {
			// Update networks
			selectMlnCombo.removeAllItems();
			for ( CyRootNetwork net : MlnReader.getMultiLayerNetworks() ) selectMlnCombo.addItem(net);
			// Enable method selection
			if ( selectMlnCombo.getItemCount() == 0 )
				Print.messageDialog( "Multi-layer network not found",
						"No multi-layer network was loaded", JOptionPane.WARNING_MESSAGE );
			else
				setEnabled(methodRadioGroup, true);
			// Enable layer selection
			selectLayersList.setEnabled(true);
			// Disable app selection
			setEnabled(appRadioGroup, false);
			// Disable extraction button
			extractButton.setEnabled(false);
			// Unselect radios
			appRadioGroup.clearSelection();
			methodRadioGroup.clearSelection();
			// Remove elements from parameters
			paramGroup.removeAll();
			paramGroup.add(new JLabel("Select an extracting app"));
		}
		
	}
	
	/*
	 * Set the type of extraction method and enable the selection of the extracting app.
	 */
	private class SetExtractionMethod implements ActionListener{
		
		ButtonGroup appRadioGroup;
		ButtonGroup methodRadioGroup;

		/*
		 * @param group of radio buttons of the extracting apps 
		 * @param group of radio buttons of the extraction method
		 */
		public SetExtractionMethod( ButtonGroup methodRadioGroup, ButtonGroup appRadioGroup ) {
			this.methodRadioGroup = methodRadioGroup;
			this.appRadioGroup = appRadioGroup;
		}
		
		@Override
		public void actionPerformed(ActionEvent e) {
			// Enable selection of extracting app
			setEnabled(appRadioGroup, true);
			// Set type of extraction method
			String methodName = methodRadioGroup.getSelection().getActionCommand();
			if ( methodName.equals("Global") ) method = new GlobalExtractionMethod();
			else if ( methodName.equals("Pairwise") ) method = new PairwiseExtractionMethod();
			else if ( methodName.equals("One By One") ) method = new OneByOneExtractionMethod();
		}
		
		
		
	}
	
	/*
	 * Update information on the flattened network when a multi-layer network is selected.
	 */
	private class UpdateFlattenNetwork implements ItemListener {
		
		JTextField flatNetworkText;
		DefaultListModel<Integer> selectLayersModel;
		JList<Integer> selectLayersList;
		JScrollPane selectLayersPane;
		
		public UpdateFlattenNetwork( JTextField flatNetworkText,
				DefaultListModel<Integer> selectLayersModel, JList<Integer> selectLayersList, JScrollPane selectLayersPane ) {
			this.flatNetworkText = flatNetworkText;
			this.selectLayersModel = selectLayersModel;
			this.selectLayersList = selectLayersList;
			this.selectLayersPane = selectLayersPane;
		}
		
		/*
		 * Update information when a multi-layer network is selected.
		 */
		@Override
		public void itemStateChanged(ItemEvent e) {
			if ( selectMlnCombo.getItemCount() > 0 ) {
				try {
					CySubNetwork flattenedNet = getFlattenedNetwork();
					// Check format of the MLN
					MlnReader.checkFlattenedNetworkFormat(flattenedNet);
					MlnReader.checkUniqueNodeNameForFlattenedNetwork(flattenedNet);
					// Update name of flatten network
					flatNetworkText.setText( flattenedNet.getDefaultNetworkTable()
							.getRow( flattenedNet.getSUID() )
							.get(CyNetwork.NAME, String.class)  );
					// Update list of layers
					selectLayersModel.removeAllElements();
					TreeSet<Integer> layers = MlnReader.getLayerIdsFromFlattenedNetwork( flattenedNet.getDefaultNodeTable() );
					IntStream.range(layers.first(), layers.last() + 1).boxed().forEach(
							item -> { selectLayersModel.addElement(item); }
							);
					selectLayersList.setSelectionInterval(0, layers.size() - 1);
					// Set size of the list of layers
					if ( layers.size() < 5 )
						setComponentSize( selectLayersPane, 80, layers.size() * 20 + 2 );
					else
						setComponentSize( selectLayersPane, 80, 5 * 20 + 1 );
					mainPanel.revalidate();
				} catch (MlnReaderException err) {
					Print.error(err);
					Print.messageDialog( err.getMessageTitle(), err.getMessage(), err.getMessageType() );
				}	
			}
		}
	}
	
	/*
	 * Add form elements for parameters of the app to the parameter section of the panel.
	 */
	private class AddParamToFormListener implements ActionListener{

		Box paramGroup;
		JList<Integer> selectLayersList;
		JButton extractButton;
		Class<? extends AppCaller> appClass;
		
		/*
		 * @param paramGroup box containing the form elements for parameters
		 * @param Jlist with the layers selected for extraction
		 * @param button executing the extraction
		 */
		public AddParamToFormListener( Box paramGroup, JList<Integer> selectLayersList, JButton extractButton,
				Class<? extends AppCaller> appClass ) {
			this.paramGroup = paramGroup;
			this.selectLayersList = selectLayersList;
			this.extractButton = extractButton;
			this.appClass = appClass;
		}
		
		/*
		 * Add form elements to the paramGroup
		 */
		@Override
		public void actionPerformed(ActionEvent e) {
			if ( getSelectMln() != null && getSelectMln().getDefaultNetworkTable() != null ) {
				// Disable list for layer selection
				selectLayersList.setEnabled(false);
				// Enable extraction button
				extractButton.setEnabled(true);
				// Add app parameters to the panel
				paramGroup.removeAll();
				List<Integer> layers = selectLayersList.getSelectedValuesList();
				try {
					app = appClass.getConstructor( CySubNetwork.class, List.class ).newInstance( getFlattenedNetwork(), layers );
				} catch (MlnReaderException err) {
					Print.messageDialog( err.getMessageTitle(), err.getMessage(), err.getMessageType() );
				} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException err) {
					Print.error(err);
				}
				app.addParametersToPanel( paramGroup, layers );
				paramGroup.revalidate();
				paramGroup.repaint();
			} else {
				Print.messageDialog( "Multi-layer network not found",
						"No multi-layer network was loaded", JOptionPane.ERROR_MESSAGE );
				// Disable extraction button
				extractButton.setEnabled(false);
				// Remove elements from parameters
				paramGroup.removeAll();
				paramGroup.add(new JLabel("Select an extracting app"));
			}
		}
	}
	
	/*
	 * Run the extraction.
	 */
	private class RunExtractionListener implements ActionListener{

		JList<Integer> selectLayersList;
		JCheckBox enableCheckMLNCheck;
		
		public RunExtractionListener( JList<Integer> selectLayersList, JCheckBox enableCheckMLNCheck ) {
			this.selectLayersList = selectLayersList;
			this.enableCheckMLNCheck = enableCheckMLNCheck;
		}
		
		/*
		 * Run the extraction according to the type of method
		 */
		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				// Set attributes of the extraction method
				method.setCheckEnabled( enableCheckMLNCheck.isSelected() );
				method.setFlattenedNet( getFlattenedNetwork() );
				method.setApp( app );
				method.setLayers( selectLayersList.getSelectedValuesList() );
				// Run extraction
				TaskIterator extractor = new TaskIterator( method );
				ServiceProvider.get(TaskManager.class).execute( extractor );
			} catch (MlnReaderException err) {
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

	@Override
	public Component getComponent() {
		return this;
	}


	@Override
	public CytoPanelName getCytoPanelName() {
		return CytoPanelName.WEST;
	}


	@Override
	public String getTitle() {
		return "TimeNexus Extractor";
	}


	@Override
	public Icon getIcon() {
		return null;
	}

}

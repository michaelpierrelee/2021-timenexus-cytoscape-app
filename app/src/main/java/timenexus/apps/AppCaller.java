package timenexus.apps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComboBox;
import javax.swing.JComponent;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.work.TaskMonitor;

import timenexus.extraction.ExtractionMethod;
import timenexus.extraction.ExtractionMethod.MlnExtractionException;
import timenexus.utils.MlnException;

/*
 * Strategy managing extracting apps.
 * 
 * The objects related to an extracting apps can 
 * 
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public interface AppCaller {
	
	/*
	 * Update the list of comboBoxes, each of them containing the boolean columns of the node table.
	 * @param flattened network of the node table
	 * @param list of JCombobBox to update with the columns
	 * @param number of layers
	 */
	public default void addBooleanQueryColNames( CySubNetwork flattenedNet, HashMap<Integer, JComboBox<String>> queryColNames, List<Integer> layers ) {
		List<String> nodeTableCols = new ArrayList<String>();
			nodeTableCols.add("--");
		for ( CyColumn col : flattenedNet.getDefaultNodeTable().getColumns() ) {
			if ( col.getType() == Boolean.class && ! col.getName().equals("selected") ) nodeTableCols.add( col.getName() );
		}
		for (int i = 0; i < layers.size(); i++) {
			queryColNames.put( layers.get(i),
					new JComboBox<String>( nodeTableCols.toArray( new String[nodeTableCols.size()] ) ) );
		}
	}
	
	/*
	 * Update the list of comboBoxes, each of them containing the string columns of the node table.
	 * @param flattened network of the node table
	 * @param list of JCombobBox to update with the columns
	 * @param number of layers
	 */
	public default void addStringQueryColNames( CySubNetwork flattenedNet, HashMap<Integer, JComboBox<String>> queryColNames, List<Integer> layers ) {
		List<String> nodeTableCols = new ArrayList<String>();
			nodeTableCols.add("--");
		for ( CyColumn col : flattenedNet.getDefaultNodeTable().getColumns() ) {
			if ( col.getType() == String.class && ! col.getName().equals("selected")
					&& ! col.getName().equals("name") && ! col.getName().equals("shared name") )
				nodeTableCols.add( col.getName() );
		}
		for (int i = 0; i < layers.size(); i++) {
			queryColNames.put( layers.get(i),
					new JComboBox<String>( nodeTableCols.toArray( new String[nodeTableCols.size()] ) ) );
		}
	}
	
	/*
	 * Add form elements of the app parameters to a JComponent.
	 * @param JComponent to which the parameters are added
	 * @param list of layer IDs with a query column
	 */
	public void addParametersToPanel( JComponent comp, List<Integer> layers );
	
	/*
	 * Get class variable of column names containing query nodes.
	 */
	public HashMap<Integer, JComboBox<String>> getQueryColNames();
	
	/*
	 * Send data to the app and get back the response.
	 * @param network to extract
	 * @param query-source node
	 * @param query-target node
	 * @param task monitor of the extraction method
	 * @param if true, the task is cancelled
	 * @return the extracted network
	 */
	public ExtractedNetwork call( CyNetwork network, Map<String, String> querySources, Map<String, String> queryTargets,
			TaskMonitor taskMonitor, ExtractionMethod method )
			throws MlnAppCallerException, MlnExtractionException;
	
	/*
	 * Check if the multi-layer network can be processed by the app.
	 * @param the flattened network
	 * @return the message if the criteria are not met
	 */
	public String checkNetwork( CyNetwork flattenedNet );
	
	/*_______________________________________
	 * 
	 *			EXCEPTION
	 *_______________________________________
	 */
	
	public static class MlnAppCallerException extends MlnException{

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public MlnAppCallerException(String message, String messageTitle, int messageType) {
			super(message, messageTitle, messageType);
		}
		
		public MlnAppCallerException(String message, String messageTitle, int messageType, Throwable cause) {
			super(message, messageTitle, messageType, cause);
		}
	}
	
}

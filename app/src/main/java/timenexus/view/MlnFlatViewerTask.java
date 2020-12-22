package timenexus.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.view.layout.CyLayoutAlgorithm;
import org.cytoscape.view.layout.CyLayoutAlgorithmManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.model.View;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.FinishStatus.Type;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskObserver;

import timenexus.temporalnetwork.MlnWriter;
import timenexus.utils.MlnException;
import timenexus.utils.Print;
import timenexus.utils.ServiceProvider;

/*
 * Enable visualization of flattened network from a multi-layer network.
 * 
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class MlnFlatViewerTask extends AbstractTask {
	
	CySubNetwork flattenedNet;
	CySubNetwork aggregatedNet;
	List<Integer> layersToShow;
	// Enable to cancel the task
	volatile boolean cancelled = false;
	
	public MlnFlatViewerTask(CySubNetwork flattenedNet, CySubNetwork aggregatedNet,
			List<Integer> layersToShow) {
		this.flattenedNet = flattenedNet;
		this.aggregatedNet = aggregatedNet;
		this.layersToShow = layersToShow;
	}
	
	/*
	 * Cancel the task.
	 */
	@Override
	public void cancel() { cancelled = true; }

	/*
	 * Load the layout of the aggregated network into each layers of the flattened network.
	 */
	public void run(TaskMonitor taskMonitor) throws MlnException {
		taskMonitor.setTitle("TimeNexus Viewer");
		taskMonitor.setStatusMessage("Getting a view from the aggregated network...");
		//		Get values
		// Get view of the aggregated network
		CyNetworkViewManager networkViewManager = ServiceProvider.get(CyNetworkViewManager.class);
		List<CyNetworkView> aggViews = new ArrayList<CyNetworkView>( networkViewManager.getNetworkViews(aggregatedNet) );
		// If needed, create view for the aggregated network
		CyNetworkViewFactory networkViewFactory = ServiceProvider.get(CyNetworkViewFactory.class);
		CyNetworkView viewAggNet;
		if ( aggViews.size() == 0 ) {
			// Create view for the aggregated network
			viewAggNet = networkViewFactory.createNetworkView( aggregatedNet );
			// Apply default layout to the view
			CyLayoutAlgorithmManager layoutManager = ServiceProvider.get(CyLayoutAlgorithmManager.class);
			CyLayoutAlgorithm defaultLayout = layoutManager.getDefaultLayout();
			TaskIterator itr = defaultLayout.createTaskIterator(viewAggNet, defaultLayout.createLayoutContext(),
					CyLayoutAlgorithm.ALL_NODE_VIEWS, null);
			AggViewCreationObserver aggViewCreationObs = new AggViewCreationObserver();
			ServiceProvider.get(TaskManager.class).execute( itr, aggViewCreationObs );
			// Wait the job to finish before to continue
			while ( ( ! aggViewCreationObs.isTaskDone() || aggViewCreationObs.isTaskCancelled() ) && ! cancelled ) {
				try { Thread.sleep(100); }
				catch (InterruptedException err) {
					throw new MlnException("Thread was interrupted.", "Interrupted thread", JOptionPane.ERROR_MESSAGE, err);
					}
				}
			// Save the view
			networkViewManager.addNetworkView(viewAggNet);
			// Update the view
			viewAggNet.updateView();
			if ( aggViewCreationObs.isTaskDone() )
				Print.messageDialog("Multiple views of the aggregated network",
						"Aggregated network does not have any view. One has been generated based on the default Cytoscape layout.",
						JOptionPane.INFORMATION_MESSAGE );
		} else if ( aggViews.size() > 1 ) {
			Print.messageDialog( "Multiple views of the aggregated network",
					"There are several views for the aggregated network. The last generated view has been selected.",
					JOptionPane.INFORMATION_MESSAGE );
			viewAggNet = aggViews.get(0);
		} else
			viewAggNet = aggViews.get(0);
		
		//		Set view of the flat network
		taskMonitor.setStatusMessage("Generating a view for the flattened network...");
		// Get size of the agg network
		double max = -99999999;
		double min = 99999999;
		for (View<CyNode> nodeView : viewAggNet.getNodeViews()) {
			double loc = nodeView.getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION);
			if ( loc > max ) max = loc;
			if ( loc < min ) min = loc;
		}
		double width = max - min;
		// Clean current views
		List<CyNetworkView> flatViews = new ArrayList<CyNetworkView>( networkViewManager.getNetworkViews(flattenedNet) );
		for (CyNetworkView view : flatViews) networkViewManager.destroyNetworkView(view);
		// Create new view
		CyNetworkView viewFlatNet = networkViewFactory.createNetworkView(flattenedNet);
		// Set location of nodes
		Map<Integer, Map<String, CyNode>> flatNodeLayers = MlnWriter.getAllNodeLayers(flattenedNet);
		for ( CyNode aggNode : aggregatedNet.getNodeList() ) {
			// Get node of the agg network
			CyRow aggNodeRow = aggregatedNet.getRow(aggNode);
			String aggNodeName = aggNodeRow.get(CyNetwork.NAME, String.class);
			List<Integer> nodeLayerIDs = aggNodeRow.getList( MlnWriter.LAYER_ID, Integer.class );
			// Get location of the node
			View<CyNode> aggNodeView = viewAggNet.getNodeView(aggNode);
			double aggNodeX = getNodeX(aggNodeView);
			double aggNodeY = getNodeY(aggNodeView);
			// Set location of the node-layer
			for ( int layerID : nodeLayerIDs ) {
				String flatNodeName = aggNodeName + "_" + layerID;
				if ( flatNodeLayers.get(layerID) != null && flatNodeLayers.get(layerID).containsKey(flatNodeName) ) {
					CyNode flatNode = flatNodeLayers.get(layerID).get(flatNodeName);
					View<CyNode> flatNodeView = viewFlatNet.getNodeView(flatNode);
					if ( layersToShow.contains(layerID) ) {
						double[] nodeLoc = computeNodeLayerLocation( aggNodeX, aggNodeY, width, layerID );
						setNodeLoc(flatNodeView, nodeLoc);
						flatNodeView.setVisualProperty(BasicVisualLexicon.NODE_VISIBLE, true);
					} else
						flatNodeView.setVisualProperty(BasicVisualLexicon.NODE_VISIBLE, false);
				}
			}
		}
		// Fit content
		viewFlatNet.fitContent();
		
		//		Register view
		networkViewManager.addNetworkView(viewFlatNet);
	}
	
	/*
	 * Get status of the task creating the Agg view.
	 */
	private class AggViewCreationObserver implements TaskObserver{
		
		private boolean taskDone = false;
		private boolean taskCancelled = false;
		
		public AggViewCreationObserver() {}

		@Override
		public void taskFinished(ObservableTask task) {}

		@Override
		public void allFinished(FinishStatus finishStatus) {
			if ( finishStatus.getType() == Type.SUCCEEDED ) taskDone = true;
			else if ( finishStatus.getType() == Type.CANCELLED ) taskCancelled = true;
		}

		public boolean isTaskDone() {
			return taskDone;
		}

		public boolean isTaskCancelled() {
			return taskCancelled;
		}
	}

	/*_______________________________________
	 * 
	 *			ALGO
	 *_______________________________________
	 */
	
	private double[] computeNodeLayerLocation( double nodeX, double nodeY, double widthLayer, int layerID ) {
		double[] nodeLoc = {0, 0};
		// X
		nodeLoc[0] = 1.25 * widthLayer * layerID + nodeX;
		// Y
		nodeLoc[1] = nodeY;
		return nodeLoc;
	}
	
	/*_______________________________________
	 * 
	 *			UTILS
	 *_______________________________________
	 */
	
	/*
	 * @return X-location of the CyNode view
	 */
	private double getNodeX(View<CyNode> view) {
		return view.getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION);
	}
	
	/*
	 * @return Y-location of the CyNode view
	 */
	private double getNodeY(View<CyNode> view) {
		return view.getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION);
	}
	
	/*
	 * Set location of the CyNode view
	 */
	private void setNodeLoc(View<CyNode> view, double[] nodeLoc) {
		view.setVisualProperty(BasicVisualLexicon.NODE_X_LOCATION, nodeLoc[0]);
		view.setVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION, nodeLoc[1]);
	}
}

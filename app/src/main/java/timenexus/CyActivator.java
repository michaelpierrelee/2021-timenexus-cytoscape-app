package timenexus;

import java.util.Properties;

import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.model.events.RowsSetListener;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.work.ServiceProperties;
import org.cytoscape.work.TaskFactory;
import org.osgi.framework.BundleContext;

import timenexus.extraction.TimeNexusExtractorPanel;
import timenexus.listeners.MlnUpdateEdgeDirectionListener;
import timenexus.temporalnetwork.CopyFlattenedNetworkPanel;
import timenexus.temporalnetwork.MlnBuildFromFlatNetworkPanel;
import timenexus.temporalnetwork.TimeNexusConverterPanel;
import timenexus.utils.RegisterTask;
import timenexus.utils.ServiceProvider;
import timenexus.view.TimeNexusViewerPanel;

/*
 * Load TimeNexus into Cytoscape.
 * 
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class CyActivator extends AbstractCyActivator {

	public CyActivator() {
		super();
	}
	
	public void start(BundleContext context) throws Exception {
		
		/**** LOAD SERVICES ****/

		ServiceProvider.setContext(context);
		
		/**** MENU ****/
		
		//		CONVERT CYNETWORKS INTO MULTILAYER NETWORK
		registerService( context, new RegisterTask<TimeNexusConverterPanel>(TimeNexusConverterPanel.class),
				TaskFactory.class, ezProps(
						ServiceProperties.PREFERRED_MENU, "Apps.TimeNexus",
						ServiceProperties.TITLE, "Convert networks or tables into MLN",
						ServiceProperties.MENU_GRAVITY, "1.0" ) );
		
		//		COPY MULTILAYER NETWORKS
		registerService( context, new RegisterTask<CopyFlattenedNetworkPanel>(CopyFlattenedNetworkPanel.class),
				TaskFactory.class, ezProps(
						ServiceProperties.PREFERRED_MENU, "Apps.TimeNexus",
					ServiceProperties.TITLE, "Copy flattened network",
					ServiceProperties.MENU_GRAVITY, "2.0" ) );
		
		//		IMPORT MULTILAYER NETWORK FROM FILE
		registerService( context, new RegisterTask<MlnBuildFromFlatNetworkPanel>(MlnBuildFromFlatNetworkPanel.class),
				TaskFactory.class, ezProps(
						ServiceProperties.PREFERRED_MENU, "Apps.TimeNexus",
					ServiceProperties.TITLE, "Build MLN from flattened network",
					ServiceProperties.MENU_GRAVITY, "3.0" ) );
		
		/**** PANELS ****/
		
		//		REGISTER VIEWER PANEL
		registerService( context, new TimeNexusViewerPanel(), CytoPanelComponent.class, new Properties() );
		
		//		REGISTER EXTRACTOR PANEL
		registerService( context, new TimeNexusExtractorPanel(), CytoPanelComponent.class, new Properties() );
		
		
		/**** LISTENERS ****/
		
		//		EDGE DIRECTION CHANGE LISTENER
		
		registerService( context, new MlnUpdateEdgeDirectionListener(), RowsSetListener.class, new Properties() );
	}
	
	/*
	 * An easier way to construct service properties
	 * @author Cytoscape
	 */
	private static Properties ezProps(String... vals) {
	    final Properties props = new Properties();
	    for (int i = 0; i < vals.length; i += 2)
	       props.put(vals[i], vals[i + 1]);
	    return props;
	}
}

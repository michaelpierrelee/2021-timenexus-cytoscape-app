package timenexus.utils;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/*
 * @return Cytoscape services on-demand
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public final class ServiceProvider {

	private static BundleContext context;
	
	private ServiceProvider(){ throw new RuntimeException(); }

	public static BundleContext getContext() {
		return context;
	}

	public static void setContext(BundleContext bc) {
		context = bc;
	}
	
	/*
	 * See the method getService from AbstractCyActivator.class and ServiceUtil.class
	 * @return the specified Cytoscape service
	 */
	public static <S> S get( Class<S> serviceClass ) {
		ServiceReference ref = context.getServiceReference(serviceClass.getName());
		return serviceClass.cast( context.getService(ref) );
	}
}

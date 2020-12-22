package timenexus.utils;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

import org.json.JSONObject;

/*
 * Manage HTTP requests.
 * 
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class HttpRequestToAPI {
	
	private HttpRequestToAPI() {}
	
	/*
	 * Send a json object to a simple REST interface.
	 * @param URL of the REST interface
	 * @param json object to send
	 */
	public static HttpResponse<String> sendJSON( String url, JSONObject json )
			throws URISyntaxException, IOException, InterruptedException {		
		HttpRequest request = HttpRequest.newBuilder()
				.uri(new URI( url ))
				.headers( "Content-Type", "application/json", "Accept", "application/json" )
				.POST( BodyPublishers.ofString( json.toString() ) )
				.build();
		
		return sendRequest(request);
	}
	
	/*
	 * Create and send a SOAP request.
	 * @param URL of the SOAP service
	 * @param String containing the XML data
	 * @param name of the SOAPAction
	 */
	public static HttpResponse<String> sendSOAP( String url, String xml, String SOAPAction )
			throws URISyntaxException, IOException, InterruptedException { 		
		HttpRequest request = HttpRequest.newBuilder()
				.uri(new URI( url ))
				.headers( "Accept", "text/xml, multipart/related",
						"Content-Type", "text/xml; charset=utf-8",
						"SOAPAction", SOAPAction )
				.POST( BodyPublishers.ofString( xml ) )
				.build();
		
		return sendRequest(request);
	}
	
	/*
	 * Send HttpRequest.
	 */
	private static HttpResponse<String> sendRequest( HttpRequest request ) throws IOException, InterruptedException {
		return HttpClient
				  .newBuilder()
				  .proxy(ProxySelector.getDefault())
				  .build()
				  .send( request, BodyHandlers.ofString() );
	}

}

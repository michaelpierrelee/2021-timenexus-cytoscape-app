package timenexus.apps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Class to store extracted network which are returned by the apps.
 * 
 * The attribute "edges" can be let empty if "edgeAttributes" is empty
 * 
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class ExtractedNetwork{
	
	private List<String> nodeNames;
	private List<List<String>> edges;
	// attributes (key: name of the attribute, value: content)
	private Map<String, ElementAttributes> nodeAttributes = new HashMap<String, ElementAttributes>();
	private Map<String, ElementAttributes> edgeAttributes = new HashMap<String, ElementAttributes>();
	
	/*
	 * @param list of node names
	 * @param list of edges, such as one edge is a List<String> with two elements (one for each node)
	 * List of edges can be null if "edgeAttributes" is empty.
	 */
	public ExtractedNetwork( List<String> nodeNames, List<List<String>> edges ) {
		this.nodeNames = nodeNames;
		this.edges = edges;
	}
	
	/*
	 * Return list of nodes.
	 */
	public List<String> getNodeNames(){ return nodeNames; }
	
	/*
	 * Return list of edges.
	 */
	public List<List<String>> getEdges(){ return edges; }
	
	/*
	 * @return names of the node attributes
	 */
	public List<String> getNodeAttributeNames(){ return new ArrayList<String>( nodeAttributes.keySet() ); }
	
	/*
	 * @return names of the edge attributes
	 */
	public List<String> getEdgeAttributeNames(){ return new ArrayList<String>( edgeAttributes.keySet() ); }
	
	/*
	 * Add one attribute to each node.
	 * @param name of the attribute
	 * @param values of the attribute (one for each node)
	 * @param type of the attribute
	 */
	public <T> void addNodeAttributes( String attributeName, List<T> values, Class<T> type ) {
		Attributes<T> A = new Attributes<T>( type );
		A.setValues(values);
		nodeAttributes.put( attributeName, A );
	}
	
	/*
	 * Add one attribute to each node.
	 * @param name of the attribute
	 * @param values of the attribute (one for each node)
	 * @param type of the attribute
	 */
	public <T> void addNodeListAttributes( String attributeName, List<List<T>> values, Class<T> type ) {
		Attributes<T> A = new Attributes<T>( type );
		A.setListValues(values);
		nodeAttributes.put( attributeName, A );
	}
	
	/*
	 * Add one attribute to each edge.
	 * @param name of the attribute
	 * @param values of the attribute (one for each edge)
	 * @param type of the attribute
	 */
	public <T> void addEdgeAttributes( String attributeName, List<T> values, Class<T> type ) {
		Attributes<T> A = new Attributes<T>( type );
		A.setValues(values);
		edgeAttributes.put( attributeName, A );
	}
	
	/*
	 * Add one attribute to each edge.
	 * @param name of the attribute
	 * @param values of the attribute (one for each edge)
	 * @param type of the attribute
	 */
	public <T> void addEdgeListAttributes( String attributeName, List<List<T>> values, Class<T> type ) {
		Attributes<T> A = new Attributes<T>( type );
		A.setListValues(values);
		edgeAttributes.put( attributeName, A );
	}
	
	/*
	 * Get type of a node attribute.
	 * @param name of the node attribute
	 */
	public Class<?> getNodeAttributeType( String attributeName ){
		return nodeAttributes.get(attributeName).getType();
	}
	/*
	 * Get type of a edge attribute.
	 * @param name of the edge attribute
	 */
	public Class<?> getEdgeAttributeType( String attributeName ){
		return edgeAttributes.get(attributeName).getType();
	}
	
	/*
	 * Get values of a given node attribute
	 */
	public List<?> getNodeAttributeValues( String attributeName ) {
		ElementAttributes attr = nodeAttributes.get(attributeName);
		if ( attr.isList() ) return attr.getListValues( attr.getType() );
		else return attr.getValues( attr.getType() );
	}

	/*
	 * Get values of a given edge attribute
	 */
	public List<?> getEdgeAttributeValues( String attributeName ) {
		ElementAttributes attr = edgeAttributes.get(attributeName);
		if ( attr.isList() ) return attr.getListValues( attr.getType() );
		else return attr.getValues( attr.getType() );
	}
	
	/*
	 * Interface to manage multiple types of attributes.
	 */
	private interface ElementAttributes{
		public Class<?> getType();
		public <T> List<T> getValues(Class<T> type);
		public <T> List<List<T>> getListValues(Class<T> type);
		public boolean isList();
	}
	
	/*
	 * Class to manage multiple attributes of an element
	 */
	private class Attributes<T> implements ElementAttributes{
		
		private List<T> values;
		private List<List<T>> listValues;
		private Class<T> type;
		private boolean isList = false;
		
		public Attributes( Class<T> type ) { this.type = type; }

		/*
		 * Set the list of values.
		 */
		public void setValues( List<T> values ) { this.values = values; }
		
		/*
		 * Set the list of list values.
		 */
		public void setListValues( List<List<T>> listValues ) {
			this.listValues = listValues;
			isList = true;
			}

		/*
		 * @return true if the object is a list of list
		 */
		public boolean isList() { return isList; }
		
		/*
		 * @return type of the attribute.
		 */
		public Class<T> getType() { return type; }

		/*
		 * @return the list of values.
		 */
		public <S> List<S> getValues( Class<S> type ) {
			List<S> newList = new ArrayList<S>();
			for (T val : values) newList.add( type.cast(val) );
			return newList;
		}
		
		/*
		 * @return the list of list values.
		 */
		public <S> List<List<S>> getListValues( Class<S> type ) {
			List<List<S>> newListList = new ArrayList<List<S>>();
			for (List<T> list : listValues) {
				List<S> newList = new ArrayList<S>();
				for (T val : list ) newList.add( type.cast(val) );
				newListList.add(newList);
				}
			return newListList;
		}
		
	}
	
}

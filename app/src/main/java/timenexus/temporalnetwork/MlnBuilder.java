package timenexus.temporalnetwork;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import javax.swing.JOptionPane;

import timenexus.utils.MlnException;

/*
 * @author Michaël Pierrelée, michael.pierrelee@univ-amu.fr
 */
public class MlnBuilder {
	
	public static final int NODE = 1;
	public static final int INTRA_EDGE = 2;
	public static final int INTER_EDGE = 3;
	
	public static final String NAME = "name";
	public static final String WEIGHT = "Weight";
	public static final String DIRECTION = "Direction";
	public static final String SOURCE = "Source";
	public static final String TARGET = "Target";
	
	
	private final int numberLayers;
	private NodeLayer[] nodeTables;
	private EdgeLayer[] intraEdgeTables;
	private EdgeLayer[] interEdgeTables;
	
	public MlnBuilder( int numberLayers ){
		this.numberLayers = numberLayers;
		nodeTables = new NodeLayer[numberLayers];
		intraEdgeTables = new EdgeLayer[numberLayers];
		interEdgeTables = new EdgeLayer[numberLayers - 1]; //because there are N-1 couplings
		for (int i = 0; i < numberLayers; i++) {
			nodeTables[i] = new NodeLayer();
			intraEdgeTables[i] = new EdgeLayer();
			if ( i < numberLayers - 1 ) interEdgeTables[i] = new EdgeLayer(); //because there are N-1 couplings
		}
	}
	

	/*_______________________________________
	 * 
	 *			SETTERS/GETTERS
	 *_______________________________________
	 */
	
	public void addOtherColum( int tableType, int layer, String columnName, Class<?> columnType, Collection<Object> rows ) throws MlnBuilderException{
		getTables(tableType)[layer].addOtherColumn( new MlnColumn<Object>( columnName, columnType, rows ) );
	}
	
	public void addNodeColumn( int tableType, int layer, Collection<String> rows ) throws MlnBuilderException {
		if ( tableType == NODE )
			nodeTables[layer].setNodes( new MlnColumn<String>( NAME, String.class, rows ) );
		else
			throw new MlnBuilderException("Add node column is possible only for node tables.");
	}
	
	public void addSourceColumn( int tableType, int layer, Collection<String> rows ) throws MlnBuilderException {
		if ( getEdgeLayerTables(tableType) instanceof EdgeLayer[] )
			getEdgeLayerTables(tableType)[layer].setSources( new MlnColumn<String>( SOURCE, String.class, rows ) );
		else
			throw new MlnBuilderException("Add source-node column is possible only for edge tables.");
	}
	
	public void addTargetColumn( int tableType, int layer, Collection<String> rows ) throws MlnBuilderException {
		if ( getEdgeLayerTables(tableType) instanceof EdgeLayer[] )
			getEdgeLayerTables(tableType)[layer].setTargets( new MlnColumn<String>( TARGET, String.class, rows ) );
		else
			throw new MlnBuilderException("Add target-node column is possible only for edge tables.");
	}
	
	public void addDirection( int tableType, int layer, Collection<Boolean> rows ) throws MlnBuilderException {
		if ( getEdgeLayerTables(tableType) instanceof EdgeLayer[] )
			getEdgeLayerTables(tableType)[layer].setDirections( new MlnColumn<Boolean>( DIRECTION, Boolean.class, rows ) );
		else
			throw new MlnBuilderException("Add direction is possible only for edge tables.");
	}	
	
	public void addWeight( int tableType, int layer, Collection<Double> rows ) throws MlnBuilderException {
		getTables(tableType)[layer].setWeights( new MlnColumn<Double>( WEIGHT, Double.class, rows ) );
	}
	
	public Layer[] getTables( int tableType ) throws MlnBuilderException {
		if (tableType == NODE) return nodeTables;
		else if (tableType == INTRA_EDGE) return intraEdgeTables;
		else if (tableType == INTER_EDGE) return interEdgeTables;
		else throw new MlnBuilderException("Unknown requested table.");
	}
	
	private EdgeLayer[] getEdgeLayerTables( int tableType ) throws MlnBuilderException {
		if (tableType == INTRA_EDGE) return intraEdgeTables;
		else if (tableType == INTER_EDGE) return interEdgeTables;
		else throw new MlnBuilderException("This is not an edge table.");
	}
	
	public int getNumberLayers() {
		return numberLayers;
	}
	
	public int getNodeLayerCount(int layer) {
		return nodeTables[layer].getNodes().size();
	}
	
	public NodeLayer[] getNodeLayerTables() {
		return nodeTables;
	}
	
	public EdgeLayer[] getIntraEdgeLayerTables() {
		return intraEdgeTables;
	}
	
	public EdgeLayer[] getInterEdgeLayerTables() {
		return interEdgeTables;
	}
	
	public NodeLayer getNodeLayerTables(int i) {
		return nodeTables[i];
	}
	
	public EdgeLayer getIntraEdgeLayerTables(int i) {
		return intraEdgeTables[i];
	}
	
	public EdgeLayer getInterEdgeLayerTables(int i) {
		return interEdgeTables[i];
	}
	
	
	/*_______________________________________
	 * 
	 *			FUNCTIONS
	 *_______________________________________
	 */
	
	/*
	 * @return: list of node-layers
	 */
	public ArrayList<ArrayList<String>> getNodeLayers() {
		ArrayList<ArrayList<String>> nodeLayers = new ArrayList<ArrayList<String>>();
		for (int layer = 0; layer < numberLayers; layer++)
			nodeLayers.add( nodeTables[layer].getNodes() );
		return nodeLayers;
	}
	
	/*
	 * @return: list of source-layers for intra-layer edges
	 */
	public ArrayList<ArrayList<String>> getIntraSourceLayers() {
		ArrayList<ArrayList<String>> sources = new ArrayList<ArrayList<String>>();
		for (int layer = 0; layer < numberLayers; layer++)
			sources.add( intraEdgeTables[layer].getSources() );
		return sources;
	}

	/*
	 * @return: list of target-layers for intra-layer edges
	 */
	public ArrayList<ArrayList<String>> getIntraTargetLayers() {
		ArrayList<ArrayList<String>> targets = new ArrayList<ArrayList<String>>();
		for (int layer = 0; layer < numberLayers; layer++)
			targets.add( intraEdgeTables[layer].getTargets() );
		return targets;
	}
	
	/*
	 * @return: list of source-layers for inter-layer edges
	 */
	public ArrayList<ArrayList<String>> getInterSourceLayers() {
		ArrayList<ArrayList<String>> sources = new ArrayList<ArrayList<String>>();
		for (int layer = 0; layer < numberLayers - 1; layer++)
			sources.add( interEdgeTables[layer].getSources() );
		return sources;
	}

	/*
	 * @return: list of target-layers for inter-layer edges
	 */
	public ArrayList<ArrayList<String>> getInterTargetLayers() {
		ArrayList<ArrayList<String>> targets = new ArrayList<ArrayList<String>>();
		for (int layer = 0; layer < numberLayers - 1; layer++)
			targets.add( interEdgeTables[layer].getTargets() );
		return targets;
	}
	
	/*
	 * @return: list of nodes across all layers, without duplicates
	 */
	public HashSet<String> getNodesAcrossLayers( ArrayList<ArrayList<String>> listOfNodes ) {
		HashSet<String> nodeList = new HashSet<String>();
		for (ArrayList<String> list : listOfNodes) {
			HashSet<String> nodes = new HashSet<String>(list);
			nodeList.addAll(nodes);
		}
		return nodeList;
	}

	/*_______________________________________
	 * 
	 *			DATA MODEL
	 *_______________________________________
	 */
	
	/*
	 * Create a column of a layer 
	 */
	public static class MlnColumn<E> extends ArrayList<E>{

		private static final long serialVersionUID = 1L;
		private String colName;
		private Class<?> colType;
		
		public MlnColumn( String colName, Class<?> colType, Collection<E> rows ) {
			super(rows);
			this.setName(colName);
			this.setType(colType);
		}
		
		public MlnColumn() {
			super();
		}

		public Class<?> getType() {
			return colType;
		}

		public void setType(Class<?> colType) {
			this.colType = colType;
		}

		public String getName() {
			return colName;
		}

		public void setName(String colName) {
			this.colName = colName;
		}
		
		public boolean equals(MlnColumn<?> comparedCol) {
			boolean identical = true;
			if ( ! colName.equals( comparedCol.getName() ) ) identical = false;
			else if ( ! colType.equals( comparedCol.getType() ) ) identical = false;
			else if ( ! this.equals( comparedCol ) ) identical = false;
			return identical;
		}
		
	}

	/*
	 * List columns for a layer
	 */
	public abstract class Layer{
		
		private MlnColumn<Double> weightColumn = new MlnColumn<Double>();
		private ArrayList<MlnColumn<?>> otherColumns = new ArrayList<MlnColumn<?>>();
		
		public MlnColumn<Double> getWeights() {
			return weightColumn;
		}
		
		public double getWeight(int index) {
			return weightColumn.get(index);
		}
		
		public void setWeights(MlnColumn<Double> weightColumn) {
			this.weightColumn = weightColumn;
		}
		public void setWeight(int index, double element) {
			this.weightColumn.set(index, element);
		}

		public ArrayList<MlnColumn<?>> getOtherColumns() {
			return otherColumns;
		}

		public void setOtherColumns(ArrayList<MlnColumn<?>> otherColumns) {
			this.otherColumns = otherColumns;
		}
		
		public void addOtherColumn(MlnColumn<?> otherColumn) {
			this.otherColumns.add(otherColumn);
		}
		
	}
	
	/*
	 * List columns for layers of nodes
	 */
	public class NodeLayer extends Layer {
		
		private MlnColumn<String> nodeColumn = new MlnColumn<String>();
		
		public NodeLayer() {}

		public MlnColumn<String> getNodes() {
			return nodeColumn;
		}

		public String getNode(int index) {
			return this.nodeColumn.get(index);
		}
		
		public void setNodes(MlnColumn<String> nodeColumn) {
			this.nodeColumn = nodeColumn;
		}

		public void setNode(int index, String element) {
			this.nodeColumn.set(index, element);
		}
	}
	
	/*
	 * List columns for layers of edges
	 */
	public class EdgeLayer extends Layer {
		
		private MlnColumn<String> sourceColumn = new MlnColumn<String>();
		private MlnColumn<String> targetColumn = new MlnColumn<String>();
		private MlnColumn<Boolean> directionColumn = new MlnColumn<Boolean>();
		
		public EdgeLayer() {}

		public MlnColumn<String> getSources() {
			return sourceColumn;
		}

		public String getSource(int index) {
			return this.sourceColumn.get(index);
		}
		
		public void setSources(MlnColumn<String> sourceColumn) {
			this.sourceColumn = sourceColumn;
		}

		public void setSource(int index, String element) {
			this.sourceColumn.set(index, element);
		}
		
		public MlnColumn<String> getTargets() {
			return targetColumn;
		}

		public String getTarget(int index) {
			return this.targetColumn.get(index);
		}
		
		public void setTargets(MlnColumn<String> targetColumn) {
			this.targetColumn = targetColumn;
		}

		public void setTarget(int index, String element) {
			this.targetColumn.set(index, element);
		}
		
		public MlnColumn<Boolean> getDirections() {
			return directionColumn;
		}

		public boolean getDirection(int index) {
			return this.directionColumn.get(index);
		}
		
		public void setDirections(MlnColumn<Boolean> directionColumn) {
			this.directionColumn = directionColumn;
		}
		
		public void setDirection(int index, boolean element) {
			this.directionColumn.set(index, element);
		}
	}
	
	/*_______________________________________
	 * 
	 *			EXCEPTION
	 *_______________________________________
	 */
	
	public static class MlnBuilderException extends MlnException {

		private static final long serialVersionUID = 1L;
		
		public MlnBuilderException(String message) {
			super(message, "Conversion error", JOptionPane.ERROR_MESSAGE);
		}
		
		public MlnBuilderException(String message, String messageTitle, int messageType) {
			super(message, messageTitle, messageType);
		}
		
		public MlnBuilderException(String message, String messageTitle, int messageType, Throwable cause) {
			super(message, messageTitle, messageType, cause);
		}
		
	}
	
}

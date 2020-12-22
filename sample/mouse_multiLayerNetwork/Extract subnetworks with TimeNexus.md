# Extract subnetworks with TimeNexus

---

Michaël Pierrelée, Aix Marseille Univ, CNRS, IBDM, UMR7288, FRANCE - <michael.pierrelee@univ-amu.fr>

*Apache License 2.0*

---

> *This workflow was applied to extract the subnetworks from the mouse dataset for the TimeNexus paper.*

The subnetworks were extracted by **TimeNexus** on **Cytoscape** (v3.8.0) with the **PathLinker** app (v1.4.2). We explain also how to do the extraction but with Anat (internet connexion required).

We followed this protocol to generate subnetworks for the yeast multilayer network:

## Import the tables
1. Load into Cytoscape the tables of the yeast multilayer network: *Toolbar > Import table from file*. For each table, select it, give it a name and import it as “unassigned table” with the default Cytoscape parameters.
   1. The tables to load: `nodeTable.tsv`, `intraLayerEdgeTable.tsv` and `interLayerEdgeTable.tsv`.

## Build the multilayer network
2. Open TimeNexus Converter: *Menu > Apps > TimeNexus > Convert networks or tables into MLN*.
3. Define how to load the multilayer network:
   1. Set the parameters: **number of layers = 3**, default weights = 1 (default), node-aligned network = true (default), edge-aligned network = true (default), **automatic inter-layer coupling = false, inter-layer coupling is equivalent = true**, intra-layer edges are directed = false (default), inter-layer edges are directed = true (default), all nodes are query nodes = false.
   2. For the node table, select the corresponding table and set the column types: column “**Node**” = type “**Node**”, columns “**Weight**” 1 to 3 = types “**Node weight layer\_**” 1 to 3, columns “**Query**” 1 to 3 = “**Other columns layer\_**”.
   3. For the intra-layer edge table, select the corresponding table and set the column types: column “**source**” = type “**Source node**”, “**target**” = “**Target node”**, “**score**” = “**Edge weight**”, “**Edge type**” = “**Shared column**”.
   4. For the inter-layer edge table, select the corresponding table and set the column types: column “**source**” = type “**Source node**”, “**target**” = “**Target node”**, columns “**Weight**” 1 > 2 to 2 > 3  = types “**Edge-weight layers\_**” 1 > 2 to 2 > 3.
   5. Click on “convert”. It will generate a multilayer network within Cytoscape. For convenience, we saved the session and loaded it again when needed.

## Extract the subnetworks
4. Run an extraction with TimeNexus: *Left-side bar > TimeNexus Extractor*.
   1. Click on “Load multi-layer networks”. If the above multilayer network is alone, it will be selected as well as its 3 layers by default.
   2. **Verify the multilayer network = false** (check the box for the first run with this multilayer network).
   3. **Select the extraction method (ex: Pairwise).**
   4. **Select the extracting app** and set its parameters:
      1. **For PathLinker: k = 50** (for example), Edge penalty = 1 (default), Edge weights = PROBABILITIES (default), Network is directed = false (default), Allow sources and targets in path = true (default), Include tied paths = false (default), cyRest port = 1234 (default if not changed by the user in the Cytoscape parameters).
      2. **For Anat: algorithm type = Anchored, sub-algorithm = Approximate**, global-local balance = 0.25 (default), Unweighted network = false (default), margin = 0 (default), edge penalty = 25 (default), enable node penalty = false (default), predict anchors = false (default), run to completion = false (default).
   5. **Select columns with query nodes:**
      1. **For Pairwise and One by One:** Layer 1 to 3 = Query 1 to 3.
      2. **For Global:** Layer 1 = Query 1 and Layer 3 = Query 3. The other layers do not matter.
   6. Click on “Extract subnetworks” and wait until completion or an error of the app. If the extraction is successful, it generates a new multilayer network called “Extracted network”.

## Analysis
5. To export a multilayer network: *right-click on its flattened network > Export as network > Export File Format = GraphML.*
6. To load back a multilayer network: *Toolbar > Import network From File > Select a flattened network which was exported*. Then, use the tool *Menu > Apps > TimeNexus > Build MLN from flattened network*.
7. Make a view of the multilayer network with TimeNexus: *Left-side bar > TimeNexus Viewer*.
   1. Click on “Load multi-layer networks”. If the above multilayer network is alone, it will be selected by default.
   2. If one wants to show the layers 1 to 3 for example: **select the layer to focus = 1, number of adjacent layers = 2**, show adjacent layers = forward (default).
   3. Update the view.

For more details, see the documentation.
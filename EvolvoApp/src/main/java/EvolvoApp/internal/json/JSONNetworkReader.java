package EvolvoApp.internal;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONArray;
import org.json.JSONException;

import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.subnetwork.CyRootNetwork;

import java.io.IOException;
import java.io.BufferedReader;

// TODO:
// - Check to see if existing specification is versatile enough to handle things like groups or metanodes.
// - Switch to a JSON library that has streaming/incremental reading support. We don't need to read
//   the entire JSON input all at once but can read it in pieces. However, this would require that
//   the "nodes" entry always appears before "edges".
// - Look for performance bottlenecks; guesses:
//   - setValue could be slow because it checks the type for each value but it could be checking by column
//   - accessing JSON values individually could be slow since it does internal type checking; could be better to
//     extract a column's values all at once to an array
class JSONNetworkReader {
    public static class InvalidContentsException extends Exception {
        public InvalidContentsException(String msgfmt, Object... args) {
            super(String.format(msgfmt, args));
        }
    }

    public static class Result {
        public List<CyNode> nodes;
        public List<CyEdge> edges;
        public List<CyNode> newNodes;
        public List<CyEdge> newEdges;
    }

    /**
     * Reads in a network specified by a JSON object.
     * This is a convenience method that parses the JSON given by {@code input}.
     * @param input A {@code BufferedReader} whose contents are a valid JSON object that specifies a network.
     * @param net The {@code CyNetwork} into which nodes and edges are created.
     * @throws InvalidContentsException if {@code input} does not follow the specification of a network
     * @throws JSONException if {@code input} is not correctly formatted JSON.
     * @throws IOException if {@code input} could not be read.
     */
    public static Result read(final BufferedReader input, final CyNetwork net) throws IOException, JSONException, InvalidContentsException {
        final JSONObject jInput = new JSONObject(new JSONTokener(input));
        return read(jInput, net);
    }

    /**
     * Reads in a network specified by a JSON object.
     * This is a convenience method by using the given network's default node, edge, and network tables
     * for the attribute tables.
     * @param jInput The JSON object representing a network.
     * @param net The {@code CyNetwork} into which nodes and edges are created.
     * @throws InvalidContentsException if {@code jInput} does not follow the specification of a network
     */
    public static Result read(final JSONObject jInput, final CyNetwork net) throws InvalidContentsException {
        return read(jInput, net, net.getDefaultNodeTable(), net.getDefaultEdgeTable(), net.getDefaultNetworkTable());
    }

    /**
     * Reads in a network specified by a JSON object.
     * @param jInput The JSON object representing a network.
     * @param net The {@code CyNetwork} into which nodes and edges are created.
     * @param nodeTable The table in which to put node attributes; this table's primary key type <i>must</i> be {@code Long} in order to accomodate node SUIDs.
     * @param edgeTable The table in which to put edge attributes; this table's primary key type <i>must</i> be {@code Long} in order to accomodate edge SUIDs.
     * @param networkTable The table in which to put network attributes.
     * @throws InvalidContentsException if {@code jInput} does not follow the specification of a network
     */
    public static Result read(final JSONObject jInput, final CyNetwork net, final CyTable nodeTable, final CyTable edgeTable, final CyTable networkTable) throws InvalidContentsException {
        final boolean hasNodes = checkTable(jInput, "nodes");
        final boolean hasEdges = checkTable(jInput, "edges");
        final boolean hasNetwork = checkTable(jInput, "network");

        final String expandOnNodeAttribute = jInput.optString("expand-on-node-attribute");
        final boolean directedEdges = jInput.optBoolean("directed-edges", false);
        final boolean duplicateEdges = jInput.optBoolean("duplicate-edges", false);

        final Result result = new Result();

        if (hasNodes) {
            buildNodes(jInput.optJSONArray("nodes"), net, nodeTable, expandOnNodeAttribute, result);
            if (hasEdges)
                buildEdges(jInput.optJSONArray("edges"), net, edgeTable, directedEdges, duplicateEdges, result);
        }
        if (hasNetwork)
            addNetworkAttrs(networkTable, jInput.optJSONArray("network"));

        return result;
    }

    /**
     * Checks to make sure a json object's value is a valid table.
     * @param jInput The encapsulating JSON object
     * @param tableElemName Name of the key in {@code jInput} whose value is a JSON array representing a table.
     * @throws InvalidContentsException If the array referenced by {@code tableElemName} is a malformed table
     * @return {@code false} if the table is valid but empty, {@code true} if the table is populated.
     */
    private static boolean checkTable(final JSONObject jInput, final String tableElemName) throws InvalidContentsException {
        if (!jInput.has(tableElemName))
            return false;

        // the json array pointed to by tableElemName
        final JSONArray jElems = jInput.optJSONArray(tableElemName);

        // make sure we have a json array at tableElemName
        if (jElems == null)
            return false;

        // Make sure no array elements in jElems is null
        for (int i = 0; i < jElems.length(); i++) {
            if (jElems.isNull(i)) {
                throw new InvalidContentsException("Element at index %d in '%s' array is null", i, tableElemName);
            }
        }

        // make sure each element in jElems is an array
        for (int i = 0; i < jElems.length(); i++) {
            final JSONArray elem = jElems.optJSONArray(i);
            if (elem == null) {
                throw new InvalidContentsException("Element at index %d in '%s' array is not an array", i, tableElemName);
            }
        }

        // make sure we have at least two arrays in jElems (one for header, one for a table entry)
        if (jElems.length() < 2)
            return false;

        final JSONArray header = jElems.optJSONArray(0);

        // make sure each header element is a non-empty string
        for (int i = 0; i < header.length(); i++) {
            final Object elem = header.opt(i);
            if (elem == null)
                throw new InvalidContentsException("Index %d of header of \'%s\' is null", i, tableElemName);
            else if (!String.class.equals(elem.getClass()))
                throw new InvalidContentsException("Index %d of header of \'%s\' is not a string", i, tableElemName);
            else if (elem.toString().length() == 0)
                throw new InvalidContentsException("Index %d of header of \'%s\' is an empty string", i, tableElemName);
        }

        // make sure each table entry has the same number of entries as the header
        for (int i = 1; i < jElems.length(); i++) {
            final JSONArray jElem = jElems.optJSONArray(i);
            if (jElem.length() != header.length()) {
                throw new InvalidContentsException("'%s' element at index %d has %d attributes, but the header has %d attribute names", tableElemName, i, jElem.length(), header.length());
            }
        }

        // figure out the types of each column
        final Class[] types = new Class[header.length()];
        for (int j = 0; j < types.length; j++) {
            for (int i = 1; i < jElems.length(); i++) {
                final Object value = jElems.optJSONArray(i).opt(j);
                if (value == null) {
                    continue;
                } else {
                    final Class type = value.getClass();
                    types[j] = type;
                    break;
                }
            }
        }

        // make sure each column has exactly the same acceptable type
        for (int j = 0; j < types.length; j++) {
            final Class expectedType = types[j];
            if (expectedType == null)
                continue;
            for (int i = 1; i < jElems.length(); i++) {
                final Object value = jElems.optJSONArray(i).opt(j);
                if (value == null)
                    continue;
                final Class type = value.getClass();
                if (!type.equals(expectedType))
                    throw new InvalidContentsException("Attribute at index %d of node at index %d has type %s but must be %s", j, i, type, expectedType);
                if (!PRIMITIVE_TYPES.contains(type))
                    throw new InvalidContentsException("Attribute at index %d of node at index %d has type %s but can only be one of these: %s", j, i, type, PRIMITIVE_TYPES);
            }
        }

        return true;
    }

    private static final Set<Class> PRIMITIVE_TYPES = new HashSet<Class>(Arrays.asList(
                String.class,
                Boolean.class,
                Integer.class,
                Double.class,
                Long.class));

    /**
     * Given a table of nodes, builds a list of nodes in the given network.
     * @param jNodes The json table of nodes to build
     * @param net The network in which to build the nodes
     * @param expandOnNodeAttribute Name of the attribute to expand upon (see below), or {@code null} if you don't
     * want to expand.
     * @return A list of {@code CyNode}s in the order they were specified in the table.
     *
     * <p>
     * <h6>Expanding on a node attribute.</h6>
     * We may want to check to see if the node already exists in
     * {@code net}. If it does, we'd want this method to return the existing node rather than
     * creating a new one. By specifying the {@code expandOnNodeAttribute} parameter,
     * the network will be scanned for the attribute in {@code expandOnNodeAttribute}. If a node matches,
     * it will be returned, otherwise a new node is created. Attributes specified in the {@code jNodes} table
     * will be copied to the node even if it already existed in the network.
     * </p>
     *
     * <p>
     * Here's an example. Let's say we have a populated network where nodes have the "name" attribute, and
     * the network already has nodes "A" and "B". We call this method with a table of nodes "A" and "C"
     * and the {@code expandOnNodeAttribute} set to "name".
     * This method will not create a new node for "A" but will create one for "C". However, if we passed
     * in null for {@code expandOnNodeAttribute}, there will be two nodes with the name "A".
     * </p>
     */
    private static void buildNodes(final JSONArray jNodes, final CyNetwork net, final CyTable table, final String expandOnNodeAttribute, final Result result) throws InvalidContentsException {
        result.nodes = new ArrayList<CyNode>();
        result.newNodes = new ArrayList<CyNode>();
        final JSONArray header = jNodes.optJSONArray(0);

        int expandIndex = -1; // the column number for expanding the nodes
        if (expandOnNodeAttribute != null && expandOnNodeAttribute.length() != 0) {
            expandIndex = findInJSONArray(header, expandOnNodeAttribute);
            if (expandIndex < 0)
                throw new InvalidContentsException("No such column \"%s\" in nodes header", expandOnNodeAttribute);
        }

        for (int i = 1; i < jNodes.length(); i++) {
            final JSONArray jNode = jNodes.optJSONArray(i);

            CyNode node = Utils.getNodeWithValue(net, table, expandOnNodeAttribute, jNode.opt(expandIndex));
            if (node == null) {
                node = net.addNode();
                result.newNodes.add(node);
            }
            result.nodes.add(node);

            final Long nodeSUID = node.getSUID();
            for (int col = 0; col < header.length(); col++) {
                final String colname = header.optString(col);
                setValue(table, nodeSUID, colname, jNode.opt(col));
            }
        }
    }

    /**
     * Look up the index of a String in a json array.
     * @return the index of the string, or -1 if the String could not be found
     */
    private static int findInJSONArray(final JSONArray array, final String value) {
        for (int col = 0; col < array.length(); col++)
            if (value.equals(array.optString(col)))
                return col;
        return -1;
    }

    /**
     * Sets an attribute value in a {@code CyTable}.
     * @param colName the name of the column in the table that'll contain the value; if the column
     * does not exist in the table, it will be created based on {@code value}'s type.
     */
    private static void setValue(final CyTable table, final Object key, final String colName, final Object value) throws InvalidContentsException {
        if (value == null)
            return;

        final Class type = value.getClass();

        // create the column if it doesn't exist
        if (table.getColumn(colName) == null) {
            table.createColumn(colName, value.getClass(), false);
        } else {
            // make sure the existing column's type matches value's type
            final Class expectedType = table.getColumn(colName).getType();
            if (!expectedType.equals(type))
                throw new InvalidContentsException("Cannot insert value \"%s\" of type %s into column \"%s\" with type %s", value, type, colName, expectedType);
        }

        final CyRow row = table.getRow(key);
             if (type.equals(String.class))  row.set(colName, (String)  value);
        else if (type.equals(Boolean.class)) row.set(colName, (Boolean) value);
        else if (type.equals(Integer.class)) row.set(colName, (Integer) value);
        else if (type.equals(Double.class))  row.set(colName, (Double)  value);
        else if (type.equals(Long.class))    row.set(colName, (Long)    value);
    }

    private static void buildEdges(final JSONArray jEdges, final CyNetwork net, final CyTable table, final boolean directedEdges, final boolean duplicateEdges, final Result result) throws InvalidContentsException {
	final CyEdge.Type edgeType = directedEdges ? CyEdge.Type.OUTGOING : CyEdge.Type.UNDIRECTED;
        result.edges = new ArrayList<CyEdge>();
        result.newEdges = new ArrayList<CyEdge>();

        final JSONArray header = jEdges.optJSONArray(0);
        
        for (int row = 1; row < jEdges.length(); row++) {
            final JSONArray jEdge = jEdges.optJSONArray(row);

            final Integer srcIndex = jEdge.optInt(0);
            final Integer trgIndex = jEdge.optInt(1);
            if (srcIndex == null || (!(0 <= srcIndex && srcIndex < result.nodes.size())))
                throw new InvalidContentsException("Edge at index %d doesn't have valid source index", row);
            if (trgIndex == null || (!(0 <= trgIndex && trgIndex < result.nodes.size())))
                throw new InvalidContentsException("Edge at index %d doesn't have valid target index", row);

            final CyNode src = result.nodes.get(srcIndex);
            final CyNode trg = result.nodes.get(trgIndex);

            CyEdge edge;
            if (duplicateEdges || !net.containsEdge(src, trg)) {
                edge = net.addEdge(src, trg, directedEdges);
                //System.out.println(String.format("New edge: %s <-> %s", Attr.Attr(net, src, "name"), Attr.Attr(net, trg, "name")));
                result.newEdges.add(edge);
            } else {
                final List<CyEdge> possibleEdges = net.getConnectingEdgeList(src, trg, edgeType);
                edge = possibleEdges.get(0);
            }
            result.edges.add(edge);

            final Long edgeSUID = edge.getSUID();
            for (int col = 2; col < jEdge.length(); col++) {
                final String colname = header.optString(col);
                setValue(table, edgeSUID, colname, jEdge.opt(col));
            }
        }
    }

    private static void addNetworkAttrs(final CyTable table, final JSONArray jAttrs) throws InvalidContentsException {
        final JSONArray header = jAttrs.optJSONArray(0);
        final int primaryKeyIndex = findInJSONArray(header, "primary-key");
        if (primaryKeyIndex < 0)
            throw new InvalidContentsException("Header does not have \"primary-key\" column in \'network\' table");
        final Class expectedPrimaryKeyType = table.getPrimaryKey().getType();
        for (int row = 1; row < jAttrs.length(); row++) {
            final JSONArray jRow = jAttrs.optJSONArray(row);
            Object primaryKey = jRow.opt(primaryKeyIndex);

            // check that the primary key object matches the primary key column;
            // promote integer objects to longs if necessary
            final Class primaryKeyType = primaryKey.getClass();
            if (primaryKeyType.equals(Integer.class) && expectedPrimaryKeyType.equals(Long.class))
                primaryKey = new Long((Integer) primaryKey);
            else if (!primaryKeyType.equals(expectedPrimaryKeyType))
                throw new InvalidContentsException("Primary key of row %d in \'network\' table has type %s but should be %s", row, primaryKeyType, expectedPrimaryKeyType);

            for (int col = 0; col < jAttrs.length(); col++) {
                if (col == primaryKeyIndex) continue;
                final String colname = header.optString(col);
                setValue(table, primaryKey, colname, jRow.opt(col));
            }
        }
    }
}


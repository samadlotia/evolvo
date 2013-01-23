package org.gladstoneinstitutes.bioinformaticscore.incload.internal;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

import java.io.BufferedReader;
import java.io.IOException;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.json.JSONException;

import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyColumn;

class JsonReader {
    public static void read(final BufferedReader input, final CyNetwork net) throws IOException, JSONException, JsonReaderException {
        final JSONObject jInput = new JSONObject(new JSONTokener(input));

        final boolean hasNodes = checkTable(jInput, "nodes");
        final boolean hasEdges = checkTable(jInput, "edges");

        final String expandOnNodeAttribute = jInput.optString("expand-on-node-attribute");
        final boolean directedEdges = jInput.optBoolean("directed-edges", false);
        final boolean duplicateEdges = jInput.optBoolean("duplicate-edges", false);

        if (hasNodes) {
            final List<CyNode> nodes = buildNodes(jInput.getJSONArray("nodes"), net, expandOnNodeAttribute);
            if (hasEdges)
                buildEdges(jInput.getJSONArray("edges"), net, nodes, directedEdges, duplicateEdges);
        }

        final boolean hasNetwork = checkTable(jInput, "network");
        if (hasNetwork)
            addNetworkAttrs(net.getDefaultNetworkTable(), jInput.getJSONArray("network"));
    }

    /**
     * Checks to make sure a json object's value is a valid table.
     * @param jInput The encapsulating JSON object
     * @param tableElemName Name of the key in {@code jInput} whose value is a JSON array representing a table.
     * @throws JsonReaderException If the table contained in {@code tableElemName} is a malformed table
     * @return {@code false} if the table is valid but empty, {@code true} if the table is populated.
     */
    private static boolean checkTable(final JSONObject jInput, final String tableElemName) throws JsonReaderException {
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
                throw new JsonReaderException("Element at index %d in '%s' array is null", i, tableElemName);
            }
        }

        // make sure each element in jElems is an array
        for (int i = 0; i < jElems.length(); i++) {
            final JSONArray elem = jElems.optJSONArray(i);
            if (elem == null) {
                throw new JsonReaderException("Element at index %d in '%s' array is not an array", i, tableElemName);
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
                throw new JsonReaderException("Index %d of header of \'%s\' is null", i, tableElemName);
            else if (!String.class.equals(elem.getClass()))
                throw new JsonReaderException("Index %d of header of \'%s\' is not a string", i, tableElemName);
            else if (elem.toString().length() == 0)
                throw new JsonReaderException("Index %d of header of \'%s\' is an empty string", i, tableElemName);
        }

        // make sure each table entry has the same number of entries as the header
        for (int i = 1; i < jElems.length(); i++) {
            final JSONArray jElem = jElems.optJSONArray(i);
            if (jElem.length() != header.length()) {
                throw new JsonReaderException("'%s' element at index %d has %d attributes, but the header has %d attribute names", tableElemName, i, jElem.length(), header.length());
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
                    throw new JsonReaderException("Attribute at index %d of node at index %d has type %s but must be %s", j, i, type, expectedType);
                if (!PRIMITIVE_TYPES.contains(type))
                    throw new JsonReaderException("Attribute at index %d of node at index %d has type %s but can only be one of these: %s", j, i, type, PRIMITIVE_TYPES);
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
    private static List<CyNode> buildNodes(final JSONArray jNodes, final CyNetwork net, final String expandOnNodeAttribute) throws JSONException, JsonReaderException {
        final CyTable table = net.getDefaultNodeTable();
        final List<CyNode> nodes = new ArrayList<CyNode>();
        final JSONArray header = jNodes.getJSONArray(0);

        int expandIndex = -1; // the column number for expanding the nodes
        if (expandOnNodeAttribute != null && expandOnNodeAttribute.length() != 0) {
            expandIndex = findInJSONArray(header, expandOnNodeAttribute);
            if (expandIndex < 0)
                throw new JsonReaderException("No such column \"%s\" in nodes header", expandOnNodeAttribute);
        }

        for (int i = 1; i < jNodes.length(); i++) {
            final JSONArray jNode = jNodes.getJSONArray(i);
            final CyNode node = getNodeOrNew(net, expandOnNodeAttribute, jNode.opt(expandIndex));
            final Long nodeSUID = node.getSUID();
            for (int col = 0; col < header.length(); col++) {
                setValue(table, nodeSUID, header.getString(col), jNode.opt(col));
            }
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * Look up the index of a String in a json array.
     * @return the index of the string, or -1 if the String could not be found
     */
    private static int findInJSONArray(final JSONArray array, final String value) throws JSONException {
        for (int col = 0; col < array.length(); col++)
            if (value.equals(array.getString(col)))
                return col;
        return -1;
    }

    /**
     * Attempt to find the node with a given attribute, or create a new node if no node was found.
     * @param net The network in which to create a new node
     * @param colName The name of the column that contains the attribute in the default
     * node table; if this is null, this method will return a new node
     * @param value The attribute's value in the column specified by {@code colName}; if this
     * is null, this method will return a new node
     */
    private static CyNode getNodeOrNew(final CyNetwork net, final String colName, final Object value) {
        if (colName == null || value == null)
            return net.addNode();
        final CyNode node = Utils.getNodeWithValue(net, net.getDefaultNodeTable(), colName, value);
        return node != null ? node : net.addNode();
    }

    /**
     * Sets an attribute value in a {@code CyTable}.
     * @param colName the name of the column in the table that'll contain the value; if the column
     * does not exist in the table, it will be created based on {@code value}'s type.
     */
    private static void setValue(final CyTable table, final Object key, final String colName, final Object value) throws JsonReaderException {
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
                throw new JsonReaderException("Cannot insert value \"%s\" of type %s into column \"%s\" with type %s", value, type, colName, expectedType);
        }

        final CyRow row = table.getRow(key);
             if (type.equals(String.class))  row.set(colName, (String)  value);
        else if (type.equals(Boolean.class)) row.set(colName, (Boolean) value);
        else if (type.equals(Integer.class)) row.set(colName, (Integer) value);
        else if (type.equals(Double.class))  row.set(colName, (Double)  value);
        else if (type.equals(Long.class))    row.set(colName, (Long)    value);
    }

    private static void buildEdges(final JSONArray jEdges, final CyNetwork net, final List<CyNode> nodes, final boolean directedEdges, final boolean duplicateEdges) throws JSONException, JsonReaderException {
        final JSONArray header = jEdges.getJSONArray(0);
        final CyTable table = net.getDefaultEdgeTable();
        
        for (int row = 1; row < jEdges.length(); row++) {
            final JSONArray jEdge = jEdges.getJSONArray(row);

            final Integer srcIndex = jEdge.optInt(0);
            final Integer trgIndex = jEdge.optInt(1);
            if (srcIndex == null || (!(0 <= srcIndex && srcIndex < nodes.size())))
                throw new JsonReaderException("Edge at index %d doesn't have valid source index", row);
            if (trgIndex == null || (!(0 <= trgIndex && trgIndex < nodes.size())))
                throw new JsonReaderException("Edge at index %d doesn't have valid target index", row);

            final CyNode src = nodes.get(srcIndex);
            final CyNode trg = nodes.get(trgIndex);

            final List<CyEdge> possibleEdges = net.getConnectingEdgeList(src, trg, directedEdges ? CyEdge.Type.OUTGOING : CyEdge.Type.UNDIRECTED);
            final CyEdge edge = (possibleEdges.size() == 0 || duplicateEdges) ? net.addEdge(src, trg, directedEdges) : possibleEdges.get(0);
            final Long edgeSUID = edge.getSUID();

            for (int col = 2; col < jEdge.length(); col++) {
                setValue(table, edgeSUID, header.getString(col), jEdge.opt(col));
            }
        }
    }

    private static void addNetworkAttrs(final CyTable table, final JSONArray jAttrs) throws JsonReaderException, JSONException {
        final JSONArray header = jAttrs.getJSONArray(0);
        final int primaryKeyIndex = findInJSONArray(header, "primary-key");
        if (primaryKeyIndex < 0)
            throw new JsonReaderException("Header does not have \"primary-key\" column in \'network\' table");
        final Class expectedPrimaryKeyType = table.getPrimaryKey().getType();
        for (int row = 1; row < jAttrs.length(); row++) {
            final JSONArray jRow = jAttrs.getJSONArray(row);
            Object primaryKey = jRow.opt(primaryKeyIndex);
            final Class primaryKeyType = primaryKey.getClass();
            if (primaryKeyType.equals(Integer.class) && expectedPrimaryKeyType.equals(Long.class))
                primaryKey = new Long((Integer) primaryKey);
            else if (!primaryKeyType.equals(expectedPrimaryKeyType))
                throw new JsonReaderException("Primary key of row %d in \'network\' table has type %s but should be %s", row, primaryKeyType, expectedPrimaryKeyType);
            for (int col = 0; col < jAttrs.length(); col++) {
                if (col == primaryKeyIndex) continue;
                setValue(table, primaryKey, header.getString(col), jRow.opt(col));
            }
        }
    }
}

class JsonReaderException extends Exception {
    public JsonReaderException(String msgfmt, Object... args) {
        super(String.format(msgfmt, args));
    }
}

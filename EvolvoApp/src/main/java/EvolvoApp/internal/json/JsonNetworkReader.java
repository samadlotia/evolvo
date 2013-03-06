package EvolvoApp.internal.json;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import java.io.IOException;

import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyIdentifiable;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.JsonParseException;

import EvolvoApp.internal.Utils;

public class JsonNetworkReader {
    public static interface NodeFactory {
        public void header(String[] cols) throws InvalidJsonException;
        public CyNode create(Object[] row, Class[] types) throws InvalidJsonException;
    }

    public static class BasicNodeFactory implements NodeFactory {
        final CyNetwork net;
        public BasicNodeFactory(final CyNetwork net) {
            this.net = net;
        }
        public void header(String[] cols) {}
        public CyNode create(Object[] row, Class[] types) {
            return net.addNode();
        }
    }

    public static class NonDuplicatingNodeFactory implements NodeFactory {
        final NodeFactory nodeFactory;
        final CyNetwork net;
        final CyTable nodeTable;
        final String nodeCol;
        int attrIndex = -1;

        public NonDuplicatingNodeFactory(final NodeFactory nodeFactory, final CyNetwork net, final CyTable nodeTable, final String nodeCol) {
            this.nodeFactory = nodeFactory;
            this.net = net;
            this.nodeTable = nodeTable;
            this.nodeCol = nodeCol;
        }

        public void header(String[] cols) throws InvalidJsonException {
            attrIndex = findInArray(cols, nodeCol);
            if (attrIndex < 0)
                throw new InvalidJsonException("No such column '%s' in given header: %s", nodeCol, Arrays.toString(cols));
            nodeFactory.header(cols);
        }

        public CyNode create(Object[] row, Class[] types) throws InvalidJsonException {
            CyNode node = Utils.getNodeWithValue(net, nodeTable, nodeCol, row[attrIndex]);
            if (node == null) {
                node = nodeFactory.create(row, types);
            }
            return node;
        }
    }

    private static <T> int findInArray(final T[] array, final T value) {
        for (int col = 0; col < array.length; col++)
            if (value.equals(array[col]))
                return col;
        return -1;
    }

    public static interface EdgeFactory {
        public void nodes(List<CyNode> nodes);
        public void header(String[] cols) throws InvalidJsonException;
        public CyEdge create(Object[] row, Class[] types) throws InvalidJsonException;
    }

    public static class BasicEdgeFactory implements EdgeFactory {
        final CyNetwork net;
        final boolean directedEdges;
        final boolean duplicateEdges;
        final CyEdge.Type edgeType;
        List<CyNode> nodes = null;
        public BasicEdgeFactory(final CyNetwork net, final boolean directedEdges, final boolean duplicateEdges) {
            this.net = net;
            this.directedEdges = directedEdges;
            this.duplicateEdges = duplicateEdges;
            this.edgeType = directedEdges ? CyEdge.Type.OUTGOING : CyEdge.Type.UNDIRECTED;
        }

        public void nodes(List<CyNode> nodes) {
            this.nodes = nodes;
        }

        public void header(String[] cols) throws InvalidJsonException {
            if (cols.length < 2)
                throw new InvalidJsonException("Not enough columns for edges -- need at least two to specify source and target edges");
        }

        boolean typesChecked = false;
        public CyEdge create(Object[] row, Class[] types) throws InvalidJsonException {
            if (!typesChecked) {
                if (!Long.class.equals(types[0]))
                    throw new InvalidJsonException("First column of edges must be a whole number but is a %s", types[0]);
                if (!Long.class.equals(types[1]))
                    throw new InvalidJsonException("Second column of edges must be a whole number but is a %s", types[1]);
                typesChecked = true;
            }

            final int srcIndex = ((Number) row[0]).intValue();
            if (!(0 <= srcIndex && srcIndex < nodes.size()))
                throw new InvalidJsonException("Invalid node index specified: %d, must be between 0 and %d", srcIndex, nodes.size());
            final int trgIndex = ((Number) row[1]).intValue();
            if (!(0 <= trgIndex && trgIndex < nodes.size()))
                throw new InvalidJsonException("Invalid node index specified: %d, must be between 0 and %d", trgIndex, nodes.size());

            final CyNode src = nodes.get(srcIndex);
            final CyNode trg = nodes.get(trgIndex);

            if (duplicateEdges || !net.containsEdge(src, trg)) {
                return net.addEdge(src, trg, directedEdges);
            } else {
                final List<CyEdge> possibleEdges = net.getConnectingEdgeList(src, trg, edgeType);
                return possibleEdges.get(0);
            }
        }
    }

    public static interface AttrHandler<T extends CyIdentifiable> {
        public void header(String[] cols) throws InvalidJsonException;
        public void row(T netObj, Object[] row, Class[] types) throws InvalidJsonException;
    }

    public static class BasicAttrHandler<T extends CyIdentifiable> implements AttrHandler<T> {
        final CyTable table;
        final int startIndex;

        public BasicAttrHandler(final CyTable table, final int startIndex) {
            this.table = table;
            this.startIndex = startIndex;
        }

        String[] colNames = null;
        boolean[] colChecked = null;
        public void header(String[] cols) {
            this.colNames = cols;
            this.colChecked = new boolean[cols.length];
        }

        public void row(T netObj, Object[] row, Class[] types) throws InvalidJsonException {
            for (int col = startIndex; col < row.length; col++) {
                final Object elem = row[col];
                if (elem == null) continue;
                final String colName = colNames[col];
                final Class type = types[col];

                if (!colChecked[col]) {
                    final CyColumn cyCol = table.getColumn(colName);
                    if (cyCol == null) {
                        table.createColumn(colName, type, false);
                    } else {
                        final Class expectedType = cyCol.getType();
                        if (!expectedType.equals(type))
                            throw new InvalidJsonException("type mismatch: attempting to insert value '%s' of type '%s' into column '%s' with type '%s'", elem, type, colName, expectedType);
                    }
                    colChecked[col] = true;
                }

                table.getRow(netObj.getSUID()).set(colName, type.cast(elem));
            }
        }
    }

    public static class NodeAttrHandler extends BasicAttrHandler<CyNode> {
        public NodeAttrHandler(final CyNetwork net) {
            super(net.getDefaultNodeTable(), 0);
        }
    }

    public static class EdgeAttrHandler extends BasicAttrHandler<CyEdge> {
        public EdgeAttrHandler(final CyNetwork net) {
            super(net.getDefaultEdgeTable(), 2);
        }
    }

    public static class NetworkAttrHandler extends BasicAttrHandler<CyNetwork> {
        public NetworkAttrHandler(final CyNetwork net) {
            super(net.getDefaultNetworkTable(), 0);
        }
    }

    public static void read(
            final JsonParser                p,
            final CyNetwork                 net)
        throws InvalidJsonException, JsonParseException, IOException {

        read(   p,
                net,
                new BasicNodeFactory(net), 
                new NodeAttrHandler(net),
                new BasicEdgeFactory(net, false, false),
                new EdgeAttrHandler(net),
                new NetworkAttrHandler(net));
    }

    public static void read(
            final JsonParser                p,
            final CyNetwork                 net,
            final NodeFactory               nodeFactory,
            final String                    expandOnNodeAttribute)
        throws InvalidJsonException, JsonParseException, IOException {

        read(   p,
                net,
                new NonDuplicatingNodeFactory(
                    nodeFactory,
                    net,
                    net.getDefaultNodeTable(),
                    expandOnNodeAttribute), 
                new NodeAttrHandler(net),
                new BasicEdgeFactory(net, false, false),
                new EdgeAttrHandler(net),
                new NetworkAttrHandler(net));
    }

    public static void read(
            final JsonParser                p,
            final CyNetwork                 net,
            final NodeFactory               nodeFactory,
            final AttrHandler<CyNode>       nodeAttrHandler,
            final EdgeFactory               edgeFactory,
            final AttrHandler<CyEdge>       edgeAttrHandler,
            final AttrHandler<CyNetwork>    netAttrHandler)
        throws InvalidJsonException, JsonParseException, IOException {

        JsonToken t = p.nextToken(); // start of network array
        if (t == null)
            return; // we got an empty json input, so just exit
        else if (!t.equals(JsonToken.START_ARRAY))
            throw new InvalidJsonException("network must be an array");

        final List<CyNode> nodes = new ArrayList<CyNode>();
        JsonTableReader.read(p, new JsonTableReader.Delegate() {
            public void header(String[] cols) throws InvalidJsonException {
                nodeFactory.header(cols);
                nodeAttrHandler.header(cols);
            }

            public void row(Object[] elems, Class[] types) throws InvalidJsonException {
                final CyNode node = nodeFactory.create(elems, types);
                nodeAttrHandler.row(node, elems, types);
                nodes.add(node);
            }

            public void done() {}
        });

        edgeFactory.nodes(nodes);
        JsonTableReader.read(p, new JsonTableReader.Delegate() {
            public void header(String[] cols) throws InvalidJsonException {
                edgeFactory.header(cols);
                edgeAttrHandler.header(cols);
            }

            public void row(Object[] elems, Class[] types) throws InvalidJsonException {
                final CyEdge edge = edgeFactory.create(elems, types);
                edgeAttrHandler.row(edge, elems, types);
            }

            public void done() {}
        });
        
        JsonTableReader.read(p, new JsonTableReader.Delegate() {
            public void header(String[] cols) throws InvalidJsonException {
                netAttrHandler.header(cols);
            }

            public void row(Object[] elems, Class[] types) throws InvalidJsonException {
                netAttrHandler.row(net, elems, types);
            }

            public void done() {}
        });

        t = p.nextToken(); // end of network array
        if (t == null)
            throw new InvalidJsonException("unexpected end of output");
        else if (!t.equals(JsonToken.END_ARRAY))
            throw new InvalidJsonException("only three elements allowed in network array");
    }
}

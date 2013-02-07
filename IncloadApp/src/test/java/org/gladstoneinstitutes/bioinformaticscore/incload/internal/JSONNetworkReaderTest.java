package org.gladstoneinstitutes.bioinformaticscore.incload.internal;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;

import java.io.BufferedReader;
import java.io.StringReader;

import java.util.List;

import org.cytoscape.model.NetworkTestSupport;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyEdge;

import org.json.JSONException;

public class JSONNetworkReaderTest
{
    CyNetwork network = null;
    CyTable nodeTable = null;
    CyTable edgeTable = null;
    @Before
    public void setup() {
        final NetworkTestSupport nts = new NetworkTestSupport();
        network = nts.getNetwork();
        nodeTable = network.getDefaultNodeTable();
        edgeTable = network.getDefaultEdgeTable();
    }

    @Test
    public void testEmptyJsonFile() throws Exception {
        JSONNetworkReader.read(str2rdr("{}"), network);
    }

    @Test
    public void testNonArrayNodes() throws Exception {
        JSONNetworkReader.read(str2rdr("{\"nodes\": 3}"), network);
    }

    @Test
    public void testEmptyNodes() throws Exception {
        final String contents =
            "{"                     +
              "\"nodes\": []"       +
            "}";
        JSONNetworkReader.read(str2rdr(contents), network);
    }

    @Test(expected=JSONNetworkReader.InvalidContentsException.class)
    public void testHeaderWrongType() throws Exception {
        final String contents =
            "{"                     +
              "\"nodes\": [" +
              "[1, 2, false]," +
              "[\"a\", \"b\", 2]" +
              "]"       +
            "}";
        JSONNetworkReader.read(str2rdr(contents), network);
    }

    @Test(expected=JSONNetworkReader.InvalidContentsException.class)
    public void testHeaderNull() throws Exception {
        final String contents =
            "{"                     +
              "\"nodes\": [" +
              "[null, \"name\", \"alt\"]," +
              "[\"a\", \"b\", 2]" +
              "]"       +
            "}";
        JSONNetworkReader.read(str2rdr(contents), network);
    }

    @Test(expected=JSONNetworkReader.InvalidContentsException.class)
    public void testHeaderEmptyString() throws Exception {
        final String contents =
            "{"                     +
              "\"nodes\": [" +
              "[\"\", \"name\", \"alt\"]," +
              "[\"a\", \"b\", 2]" +
              "]"       +
            "}";
        JSONNetworkReader.read(str2rdr(contents), network);
    }

    @Test(expected=JSONNetworkReader.InvalidContentsException.class)
    public void testHeaderMismatchElems() throws Exception {
        final String contents =
            "{"                     +
              "\"nodes\": [" +
              "[\"name\", \"alt\", \"size\"]," +
              "[\"A\", \"alif\"]" +
              "]"       +
            "}";
        JSONNetworkReader.read(str2rdr(contents), network);
    }

    @Test(expected=JSONNetworkReader.InvalidContentsException.class)
    public void testMismatchTypes() throws Exception {
        final String contents =
            "{"                     +
              "\"nodes\": [" +
                "[\"name\", \"alt\",   \"size\"]," +
                "[\"A\",    \"alif\",  20]," +
                "[\"B\",    \"ba\",    false]" +
              "]"       +
            "}";
        JSONNetworkReader.read(str2rdr(contents), network);
    }

    @Test(expected=JSONNetworkReader.InvalidContentsException.class)
    public void testInvalidType() throws Exception {
        final String contents =
            "{"                     +
              "\"nodes\": [" +
                "[\"name\", \"alt\",   \"size\"]," +
                "[\"A\",    \"alif\",  {\"invalid\": 10}]," +
                "[\"B\",    \"ba\",    {\"invalid\": 20}]" +
              "]"       +
            "}";
        JSONNetworkReader.read(str2rdr(contents), network);
    }

    @Test
    public void testNodeAttrs() throws Exception {
        final String contents =
            "{"                     +
              "\"nodes\": [" +
                "[\"name\", \"alt\",   \"size\"]," +
                "[\"A\",    \"alif\",  20]," +
                "[\"B\",    \"ba\",    30]" +
              "]"       +
            "}";
        JSONNetworkReader.read(str2rdr(contents), network);

        assertNotNull(Utils.getNodeWithValue(network, nodeTable, "name", "A"));
        assertNotNull(Utils.getNodeWithValue(network, nodeTable, "name", "B"));
        assertNotNull(Utils.getNodeWithValue(network, nodeTable, "alt", "alif"));
        assertNotNull(Utils.getNodeWithValue(network, nodeTable, "alt", "ba"));
        assertNotNull(Utils.getNodeWithValue(network, nodeTable, "size", new Integer(20)));
        assertNotNull(Utils.getNodeWithValue(network, nodeTable, "size", new Integer(30)));
    }

    @Test
    public void testExpandNetwork() throws Exception {
        final String contents =
            "{"                     +
              "\"nodes\": [" +
                "[\"name\"]," +
                "[\"A\"]," +
                "[\"B\"]," +
              "]"       +
            "}";
        final JSONNetworkReader.Result result = JSONNetworkReader.read(str2rdr(contents), network);

        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "A").size() == 1);
        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "B").size() == 1);

        assertTrue(result.nodes.size() == 2);
        assertTrue(result.newNodes.size() == 2);

        final String contents2 =
            "{"                     +
              "\"nodes\": [" +
                "[\"name\"]," +
                "[\"A\"]," +
                "[\"C\"]," +
              "],"       +
              "\"expand-on-node-attribute\": \"name\"" +
            "}";
        final JSONNetworkReader.Result result2 = JSONNetworkReader.read(str2rdr(contents2), network);

        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "A").size() == 1);
        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "B").size() == 1);
        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "C").size() == 1);

        assertTrue(result2.nodes.size() == 2);
        assertTrue(result2.newNodes.size() == 1);
    }

    @Test(expected=JSONNetworkReader.InvalidContentsException.class)
    public void testExpandNetworkTypeMismatch() throws Exception {
        final String contents =
            "{"                     +
              "\"nodes\": [" +
                "[\"name\"]," +
                "[\"A\"]," +
                "[\"B\"]," +
              "]"       +
            "}";
        JSONNetworkReader.read(str2rdr(contents), network);

        final String contents2 =
            "{"                     +
              "\"nodes\": [" +
                "[\"name\"]," +
                "[1]," +
                "[2]," +
              "],"       +
              "\"expand-on-node-attribute\": \"name\"" +
            "}";
        JSONNetworkReader.read(str2rdr(contents2), network);
    }

    @Test
    public void testEdges() throws Exception {
        final String contents =
            "{"                     +
              "\"nodes\": [" +
                "[\"name\"]," +
                "[\"A\"]," +
                "[\"B\"]," +
                "[\"C\"]," +
              "],"       +
              "\"edges\": [" +
                "[\"src\", \"trg\", \"weight\"]," +
                "[0      , 1      , 10]," + 
                "[0      , 2      , 20]," +
                "[1      , 2      , 30]," +
              "],"       +
            "}";
        JSONNetworkReader.read(str2rdr(contents), network);

        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "A").size() == 1);
        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "B").size() == 1);
        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "C").size() == 1);

        final CyNode nodeA = Utils.getNodeWithValue(network, nodeTable, "name", "A");
        final CyNode nodeB = Utils.getNodeWithValue(network, nodeTable, "name", "B");
        final CyNode nodeC = Utils.getNodeWithValue(network, nodeTable, "name", "C");

        final List<CyEdge> edgesAB = network.getConnectingEdgeList(nodeA, nodeB, CyEdge.Type.UNDIRECTED);
        assertTrue(edgesAB.size() == 1);
        assertEquals(edgeTable.getRow(edgesAB.get(0).getSUID()).get("weight", Integer.class), new Integer(10));

        final List<CyEdge> edgesAC = network.getConnectingEdgeList(nodeA, nodeC, CyEdge.Type.UNDIRECTED);
        assertTrue(edgesAC.size() == 1);
        assertEquals(edgeTable.getRow(edgesAC.get(0).getSUID()).get("weight", Integer.class), new Integer(20));

        final List<CyEdge> edgesBC = network.getConnectingEdgeList(nodeB, nodeC, CyEdge.Type.UNDIRECTED);
        assertTrue(edgesBC.size() == 1);
        assertEquals(edgeTable.getRow(edgesBC.get(0).getSUID()).get("weight", Integer.class), new Integer(30));
    }

    @Test(expected=JSONNetworkReader.InvalidContentsException.class)
    public void testNetworkNoPrimaryKey() throws Exception {
        final String contents =
            "{"                     +
              "\"network\": [" +
                "[\"X\", \"Y\"]," +
                "[\"a\", \"b\"]," +
              "],"       +
            "}";
        JSONNetworkReader.read(str2rdr(contents), network);
    }

    @Test
    public void testNetwork() throws Exception {
        final String contents =
            "{"                     +
              "\"network\": [" +
                "[\"primary-key\", \"roman\", \"greek\"]," +
                "[0      , \"a\"    , \"alpha\"]," +
                "[1      , \"b\"    , \"beta\"]," +
              "],"       +
            "}";
        JSONNetworkReader.read(str2rdr(contents), network);
    }

    private static BufferedReader str2rdr(final String input) {
        return new BufferedReader(new StringReader(input));
    }
}

package EvolvoApp.internal.json;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;

import java.io.BufferedReader;
import java.io.StringReader;

import java.util.Arrays;
import java.util.List;

import org.cytoscape.model.NetworkTestSupport;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyEdge;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;

import EvolvoApp.internal.Utils;

public class JsonNetworkReaderTest
{
    final JsonFactory json = new JsonFactory();
    CyNetwork network = null;
    CyTable nodeTable = null;
    CyTable edgeTable = null;
    CyTable networkTable = null;
    @Before
    public void setup() {
        final NetworkTestSupport nts = new NetworkTestSupport();
        network = nts.getNetwork();
        nodeTable = network.getDefaultNodeTable();
        edgeTable = network.getDefaultEdgeTable();
        networkTable = network.getDefaultNetworkTable();
    }

    @Test
    public void testEmptyJsonFile() throws Exception {
        JsonNetworkReader.read(json.createJsonParser("[[], [], []]"), network);
    }

    @Test
    public void testNodes() throws Exception {
        final String contents =
            "[\n"                     +
              "[\n" +
                "[\"name\", \"alt\",   \"size\"]," +
                "[\"A\",    \"aleph\",  20]," +
                "[\"B\",    \"bet\",    30]" +
              "],"       +
              "[], []" +
            "]";
        JsonNetworkReader.read(json.createJsonParser(contents), network);

        assertEquals(nodeTable.getColumn("name").getType(), String.class);
        assertEquals(nodeTable.getColumn("alt").getType(), String.class);
        assertEquals(nodeTable.getColumn("size").getType(), Long.class);
        assertNotNull(Utils.getNodeWithValue(network, nodeTable, "name", "A"));
        assertNotNull(Utils.getNodeWithValue(network, nodeTable, "name", "B"));
        assertNotNull(Utils.getNodeWithValue(network, nodeTable, "alt", "aleph"));
        assertNotNull(Utils.getNodeWithValue(network, nodeTable, "alt", "bet"));
        assertNotNull(Utils.getNodeWithValue(network, nodeTable, "size", new Long(20)));
        assertNotNull(Utils.getNodeWithValue(network, nodeTable, "size", new Long(30)));
    }

    @Test
    public void testExpandNetwork() throws Exception {
        final String contents =
            "["                     +
              "[" +
                "[\"name\"]," +
                "[\"A\"]," +
                "[\"B\"]" +
              "],"       +
              "[], []" +
            "]";
        JsonNetworkReader.read(json.createJsonParser(contents), network, new JsonNetworkReader.BasicNodeFactory(network), "name");

        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "A").size() == 1);
        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "B").size() == 1);

        final String contents2 =
            "["                     +
              "[" +
                "[\"name\"]," +
                "[\"A\"]," +
                "[\"C\"]" +
              "],"       +
              "[], []" +
            "]";
        JsonNetworkReader.read(json.createJsonParser(contents2), network, new JsonNetworkReader.BasicNodeFactory(network), "name");

        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "A").size() == 1);
        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "B").size() == 1);
        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "C").size() == 1);
    }

    @Test(expected=InvalidJsonException.class)
    public void testExpandNetworkTypeMismatch() throws Exception {
        final String contents =
            "["                     +
              "[" +
                "[\"name\"]," +
                "[\"A\"]," +
                "[\"B\"]" +
              "],"       +
              "[], []" +
            "]";
        JsonNetworkReader.read(json.createJsonParser(contents), network, new JsonNetworkReader.BasicNodeFactory(network), "name");

        final String contents2 =
            "["                     +
              "[" +
                "[\"name\"]," +
                "[0]," +
                "[1]" +
              "],"       +
              "[], []" +
            "]";
        JsonNetworkReader.read(json.createJsonParser(contents2), network, new JsonNetworkReader.BasicNodeFactory(network), "name");
    }

    @Test
    public void testEdges() throws Exception {
        final String contents =
            "["                     +
              "[" +
                "[\"name\"]," +
                "[\"A\"]," +
                "[\"B\"]," +
                "[\"C\"]" +
              "],"       +
              "[" +
                "[\"src\", \"trg\", \"weight\"]," +
                "[0      , 1      , 10]," + 
                "[0      , 2      , 20]," +
                "[1      , 2      , 30]" +
              "], []"       +
            "]";
        JsonNetworkReader.read(json.createJsonParser(contents), network);

        assertEquals(nodeTable.getColumn("name").getType(), String.class);
        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "A").size() == 1);
        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "B").size() == 1);
        assertTrue(Utils.getNodesWithValue(network, nodeTable, "name", "C").size() == 1);

        final CyNode nodeA = Utils.getNodeWithValue(network, nodeTable, "name", "A");
        final CyNode nodeB = Utils.getNodeWithValue(network, nodeTable, "name", "B");
        final CyNode nodeC = Utils.getNodeWithValue(network, nodeTable, "name", "C");

        final List<CyEdge> edgesAB = network.getConnectingEdgeList(nodeA, nodeB, CyEdge.Type.UNDIRECTED);
        assertTrue(edgesAB.size() == 1);
        assertEquals(edgeTable.getRow(edgesAB.get(0).getSUID()).get("weight", Long.class), new Long(10));

        final List<CyEdge> edgesAC = network.getConnectingEdgeList(nodeA, nodeC, CyEdge.Type.UNDIRECTED);
        assertTrue(edgesAC.size() == 1);
        assertEquals(edgeTable.getRow(edgesAC.get(0).getSUID()).get("weight", Long.class), new Long(20));

        final List<CyEdge> edgesBC = network.getConnectingEdgeList(nodeB, nodeC, CyEdge.Type.UNDIRECTED);
        assertTrue(edgesBC.size() == 1);
        assertEquals(edgeTable.getRow(edgesBC.get(0).getSUID()).get("weight", Long.class), new Long(30));
    }

    @Test
    public void testNetwork() throws Exception {
        final String contents =
            "["                     +
              "[], []," +
              "[" +
                "[\"index\", \"roman\", \"greek\"]," +
                "[1        , \"a\"    , \"alpha\"]" +
              "]"       +
            "]";
        JsonNetworkReader.read(json.createJsonParser(contents), network);

        assertEquals(networkTable.getColumn("index").getType(), Long.class);
        assertEquals(networkTable.getColumn("roman").getType(), String.class);
        assertEquals(networkTable.getColumn("greek").getType(), String.class);

        assertEquals(networkTable.getColumn("index").getValues(Long.class), Arrays.asList(1L));
        assertEquals(networkTable.getColumn("roman").getValues(String.class), Arrays.asList("a"));
        assertEquals(networkTable.getColumn("greek").getValues(String.class), Arrays.asList("alpha"));
    }
}

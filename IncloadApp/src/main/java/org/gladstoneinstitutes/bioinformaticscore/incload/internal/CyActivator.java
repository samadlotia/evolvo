package org.gladstoneinstitutes.bioinformaticscore.incload.internal;

import java.net.URL;
import java.net.URLConnection;
import java.io.InputStream;
import java.io.IOException;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import org.json.JSONTokener;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.Collection;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;

import org.osgi.framework.BundleContext;

import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyRow;

import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;

import org.cytoscape.event.CyEventHelper;

import org.cytoscape.group.CyGroup;
import org.cytoscape.group.CyGroupFactory;
import org.cytoscape.group.CyGroupManager;

import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.view.model.View;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.vizmap.VisualMappingManager;

import org.cytoscape.view.layout.CyLayoutAlgorithm;
import org.cytoscape.view.layout.CyLayoutAlgorithmManager;

import org.cytoscape.work.Task;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;
import static org.cytoscape.work.ServiceProperties.*;

import org.cytoscape.task.NodeViewTaskFactory;

public class CyActivator extends AbstractCyActivator
{
    static final Logger logger = LoggerFactory.getLogger("CyUserMessages");

    static CyNetworkFactory netFct = null;
    static CyNetworkManager netMgr = null;
    static CyNetworkViewFactory netViewFct = null;
    static CyNetworkViewManager netViewMgr = null;
    static CyGroupFactory grpFct = null;
    static CyGroupManager grpMgr = null;
    static CyLayoutAlgorithmManager layoutMgr = null;
    static CyEventHelper eventHelper = null;
    static VisualMappingManager vizMapMgr = null;

    public CyActivator()
    {
        super();
    }

    private static Properties ezProps(String... vals)
    {
        final Properties props = new Properties();
        for (int i = 0; i < vals.length; i += 2)
            props.put(vals[i], vals[i + 1]);
        return props;
    }

    public void start(BundleContext bc)
    {
        netFct = getService(bc, CyNetworkFactory.class);
        netMgr = getService(bc, CyNetworkManager.class);
        netViewFct = getService(bc, CyNetworkViewFactory.class);
        netViewMgr = getService(bc, CyNetworkViewManager.class);
        grpFct = getService(bc, CyGroupFactory.class);
        grpMgr = getService(bc, CyGroupManager.class);
        layoutMgr = getService(bc, CyLayoutAlgorithmManager.class);
        eventHelper = getService(bc, CyEventHelper.class);
        vizMapMgr = getService(bc, VisualMappingManager.class);

        registerService(bc, new TaskFactory()
        {
            public TaskIterator createTaskIterator()
            {
                return new TaskIterator(new LoadStartNetworkTask());
            }

            public boolean isReady()
            {
                return true;
            }
        }, TaskFactory.class, ezProps(
            TITLE, "Incload",
            PREFERRED_MENU, "Apps"
        ));

        registerService(bc, new NodeViewTaskFactory()
        {
            public TaskIterator createTaskIterator(View<CyNode> nodeView, CyNetworkView netView)
            {
                return new TaskIterator(new ExpandTask(nodeView, netView));
            }

            public boolean isReady(View<CyNode> nodeView, CyNetworkView netView)
            {
                return (getStartNetworkURL(netView.getModel()) != null)
                    && (!isExpanded(netView.getModel(), nodeView.getModel()));
            }

        }, NodeViewTaskFactory.class, ezProps(
            TITLE, "Incload: Expand",
            PREFERRED_MENU, "Apps"
        ));

        registerService(bc, new NodeViewTaskFactory()
        {
            public TaskIterator createTaskIterator(View<CyNode> nodeView, CyNetworkView netView)
            {
                return new TaskIterator(new CollapseTask(nodeView, netView));
            }

            public boolean isReady(View<CyNode> nodeView, CyNetworkView netView)
            {
                return (getStartNetworkURL(netView.getModel()) != null)
                    && (isExpanded(netView.getModel(), nodeView.getModel()));
            }

        }, NodeViewTaskFactory.class, ezProps(
            TITLE, "Incload: Collapse",
            PREFERRED_MENU, "Apps"
        ));
    }

    private static void setStartNetworkURL(final CyNetwork net, final String parentPath)
    {
        final CyTable netTable = net.getTable(CyNetwork.class, CyNetwork.LOCAL_ATTRS);
        if (netTable.getColumn("StartNetworkURL") == null)
            netTable.createColumn("StartNetworkURL", String.class, true);
        netTable.getRow(net.getSUID()).set("StartNetworkURL", parentPath);
    }

    private static String getStartNetworkURL(final CyNetwork net)
    {
        final CyTable netTable = net.getTable(CyNetwork.class, CyNetwork.LOCAL_ATTRS);
        return netTable.getRow(net.getSUID()).get("StartNetworkURL", String.class);
    }

    private static void applyLayout(final CyNetworkView netView, final TaskMonitor monitor) throws Exception
    {
        final CyLayoutAlgorithm alg = layoutMgr.getLayout("hierarchical");
        final Set<View<CyNode>> nodes = new HashSet<View<CyNode>>(netView.getNodeViews());
        final TaskIterator tasks = alg.createTaskIterator(netView, alg.getDefaultLayoutContext(), nodes, null);
        while (tasks.hasNext())
            tasks.next().run(monitor);
        vizMapMgr.getCurrentVisualStyle().apply(netView);
    }

    private static JSONObject jsonFromURL(final String url) throws IOException, JSONException
    {
        final InputStream input = (new URL(url)).openConnection().getInputStream();
        final JSONObject obj = new JSONObject(new JSONTokener(input));
        input.close();
        return obj;
    }

    private static CyNetwork newNetwork(final String name)
    {
        final CyNetwork net = netFct.createNetwork();
        net.getDefaultNetworkTable().getRow(net.getSUID()).set(CyNetwork.NAME, name);
        netMgr.addNetwork(net);
        return net;
    }

    private static String getNetworkName(final CyNetwork net)
    {
        return net.getDefaultNetworkTable().getRow(net.getSUID()).get(CyNetwork.NAME, String.class);
    }

    private static CyNetworkView newNetworkView(final CyNetwork net)
    {
        final CyNetworkView netView = netViewFct.createNetworkView(net);
        netViewMgr.addNetworkView(netView);
        return netView;
    }

    private static CyTable getSharedNodeTable(final CyNetwork net)
    {
        return ((CySubNetwork) net).getRootNetwork().getTable(CyNode.class, CyRootNetwork.SHARED_ATTRS);
    }

    private static CyNode newNode(final CyNetwork net, final String name)
    {
        final CyNode node = net.addNode();
        net.getDefaultNodeTable().getRow(node.getSUID()).set("name", name);
        return node;
    }


    private static Set<CyNode> getNodesWithValue(
            final CyNetwork net, final CyTable table,
            final String colname, final Object value)
    {
        System.out.println(String.format("getNodesWithValue: %s -> %s", colname, value));
        final Collection<CyRow> matchingRows = table.getMatchingRows(colname, value);
        final Set<CyNode> nodes = new HashSet<CyNode>();
        final String primaryKeyColname = table.getPrimaryKey().getName();
        for (final CyRow row : matchingRows)
        {
            final Long nodeId = row.get(primaryKeyColname, Long.class);
            if (nodeId == null)
                continue;
            System.out.println(String.format("Matching row: %s", nodeId));
            final CyNode node = net.getNode(nodeId);
            if (node == null)
                continue;
            nodes.add(node);
        }
        System.out.println();
        return nodes;
    }


    private static CyNode getNodeWithValue(
            final CyNetwork net, final CyTable table,
            final String colname, final Object value)
    {
        final Set<CyNode> nodes = getNodesWithValue(net, table, colname, value);
        if (nodes.size() == 0)
            return null;
        return nodes.iterator().next();
    }

    private static CyNode getNodeWithName(final CyNetwork net, final String name)
    {
        return getNodeWithValue(net, net.getDefaultNodeTable(), "name", name);
    }

    private static String getNodeName(final CyNetwork net, final CyNode node)
    {
        return net.getDefaultNodeTable().getRow(node.getSUID()).get("name", String.class);
    }

    private static String[] jsonStringToArray(final JSONArray jsonStrings)
    {
        final int len = jsonStrings.length();
        final String[] array = new String[len];
        for (int i = 0; i < len; i++)
            array[i] = jsonStrings.optString(i);
        //System.out.println(String.format("json conv: %s -> %s", jsonStrings, Arrays.toString(array)));
        return array;
    }

    private static String[][] jsonArrayOfStringsToArrays(final JSONArray jsonArrayOfStrings)
    {
        final int len = jsonArrayOfStrings.length();
        final String[][] arrays = new String[len][];
        for (int i = 0; i < len; i++)
        {
            final JSONArray array = jsonArrayOfStrings.optJSONArray(i);
            if (array != null)
                arrays[i] = jsonStringToArray(array);
        }
        return arrays;
    }

    private static Map<String,CyNode> mkNameToNodeMap(
            final CyNetwork net,
            final String[] names,
            final Set<CyNode> newNodes)
    {
        final Map<String,CyNode> nameToNodeMap = new HashMap<String,CyNode>();
        final CyTable table = net.getDefaultNodeTable();
        for (final String name : names)
        {
            //System.out.print("Node: " + name + " ");
            CyNode node = getNodeWithName(net, name);
            if (node == null)
            {
                node = newNode(net, name);
                newNodes.add(node);
            }
            //System.out.println(node.getSUID());
            nameToNodeMap.put(name, node);
        }
        //eventHelper.flushPayloadEvents();
        return nameToNodeMap;
    }

    private static void mkEdges(final CyNetwork net, final Map<String,CyNode> nameToNodeMap, final String[][] edges)
    {
        /*
        System.out.println("nameToNodeMap:");
        for (final Map.Entry<String,CyNode> entry : nameToNodeMap.entrySet())
        {
            System.out.println(String.format("\"%s\" -> %d", entry.getKey(), entry.getValue().getSUID()));
        }
        System.out.println();
        */

        for (final String[] edge : edges)
        {
            //System.out.println(String.format("Edge: %s", Arrays.toString(edge)));
            final String src = edge[0];
            final String trg = edge[1];
            //System.out.println(String.format("Edge: %s -> %s", src, trg));
            final CyNode srcNode = nameToNodeMap.get(src);
            //System.out.println(String.format("Src: %s - %d", src, srcNode.getSUID()));
            final CyNode trgNode = nameToNodeMap.get(trg);
            //System.out.println(String.format("Trg: %s - %d", trg, trgNode.getSUID()));
            if (!net.containsEdge(srcNode, trgNode))
                net.addEdge(srcNode, trgNode, false);
        }
        //eventHelper.flushPayloadEvents();
    }

    private static final String PARENT_COL_NAME = "IncloadParentNodeSUID";

    private static void setParentNode(final CyNetwork net, final Iterable<CyNode> nodes, final CyNode parentNode)
    {
        final CyTable table = net.getTable(CyNode.class, CyNetwork.LOCAL_ATTRS);
        final CyColumn parentCol = table.getColumn(PARENT_COL_NAME);
        if (parentCol == null)
            table.createColumn(PARENT_COL_NAME, Long.class, false);

        final Long parentSUID = parentNode.getSUID();
        for (final CyNode node : nodes)
        {
            final long nodeSUID = node.getSUID();
            if (table.getRow(nodeSUID).get(PARENT_COL_NAME, Long.class) == null)
                table.getRow(nodeSUID).set(PARENT_COL_NAME, parentSUID);
        }
    }

    private static CyNode getParentNode(final CyNetwork net, final CyNode node)
    {
        final CyTable table = net.getTable(CyNode.class, CyNetwork.LOCAL_ATTRS);
        final Long parentSUID = table.getRow(node.getSUID()).get(PARENT_COL_NAME, Long.class);
        if (parentSUID == null)
            return null;
        return net.getNode(parentSUID);
    }

    private static Set<CyNode> getNodesWithParent(final CyNetwork net, final CyNode parent)
    {
        final CyTable table = net.getTable(CyNode.class, CyNetwork.LOCAL_ATTRS);
        return getNodesWithValue(net, table, PARENT_COL_NAME, parent.getSUID());
    }

    private static final String EXPANDED_COL_NAME = "IncloadExpandedState";

    private static void setExpandedState(final CyNetwork net, final CyNode node, final boolean state)
    {
        final CyTable table = net.getTable(CyNode.class, CyNetwork.LOCAL_ATTRS);
        CyColumn expandedCol = table.getColumn(EXPANDED_COL_NAME);
        if (expandedCol == null)
            table.createColumn(EXPANDED_COL_NAME, Boolean.class, false);

        table.getRow(node.getSUID()).set(EXPANDED_COL_NAME, state);
    }

    private static boolean isExpanded(final CyNetwork net, final CyNode node)
    {
        final CyTable table = net.getTable(CyNode.class, CyNetwork.LOCAL_ATTRS);
        final Boolean state = table.getRow(node.getSUID()).get(EXPANDED_COL_NAME, Boolean.class);
        return state != null ? state : false;
    }

    private static void expand(
            final CyNetwork net,
            final JSONObject contents,
            final CyNode parentNode) throws JSONException
    {
        final JSONArray jsonNodes = contents.getJSONArray("nodes");
        final String[] nodeNames = jsonStringToArray(jsonNodes);
        final Set<CyNode> newNodes = new HashSet<CyNode>();
        final Map<String,CyNode> nameToNodeMap = mkNameToNodeMap(net, nodeNames, newNodes);

        for (final CyNode node : newNodes)
            setExpandedState(net, node, false);

        if (parentNode != null)
        {
            setParentNode(net, newNodes, parentNode);
            setExpandedState(net, parentNode, true);
            nameToNodeMap.put(getNodeName(net, parentNode), parentNode);
        }

        final JSONArray jsonEdges = contents.getJSONArray("edges");
        final String[][] edges = jsonArrayOfStringsToArrays(jsonEdges);
        mkEdges(net, nameToNodeMap, edges);

        eventHelper.flushPayloadEvents();
    }

    private static void collapseNode(final CyNetwork net, final CyNode node)
    {
        setExpandedState(net, node, false);

        final Set<CyNode> children = getNodesWithParent(net, node);
        //System.out.println("Found children:");
        //for (final CyNode child : children)
            //System.out.println(getNodeName(net, child));
        //System.out.println();
        
        // Collapse any of our children if they were expanded
        for (final CyNode child : children)
            if (isExpanded(net, child))
                collapseNode(net, child);

        net.removeNodes(children);
    }

    public static class LoadStartNetworkTask implements Task
    {
        @Tunable(description="URL")
        public String url = "http://localhost:8000/";

        public void run(final TaskMonitor monitor) throws Exception
        {
            monitor.setTitle("Incload: Opening network");
            monitor.setStatusMessage("Downloading from " + url);

            final JSONObject contents = jsonFromURL(url);
            final CyNetwork net = newNetwork(String.format("%s (Incremental Network)", url));
            //System.out.println("INCLOAD: new net: " + net.getSUID());
            expand(net, contents, null);

            setStartNetworkURL(net, url);
            final CyNetworkView netView = newNetworkView(net);
            applyLayout(netView, monitor);
        }

        public void cancel() {}
    }

    public static class ExpandTask implements Task
    {
        final View<CyNode> nodeView;
        final CyNetworkView netView;

        public ExpandTask(View<CyNode> nodeView, CyNetworkView netView)
        {
            this.nodeView = nodeView;
            this.netView = netView;
        }

        public void run(TaskMonitor monitor) throws Exception
        {
            final CyNetwork net = netView.getModel();
            final CyNode node = nodeView.getModel();
            //System.out.println("INCLOAD: expansion: " + net.getSUID());

            final String startURL = getStartNetworkURL(net);
            final String url = startURL + getNodeName(net, node);

            final JSONObject contents = jsonFromURL(url);
            expand(net, contents, node);

            applyLayout(netView, monitor);
        }

        public void cancel() { }
    }

    public static class CollapseTask implements Task
    {
        final View<CyNode> nodeView;
        final CyNetworkView netView;

        public CollapseTask(View<CyNode> nodeView, CyNetworkView netView)
        {
            this.nodeView = nodeView;
            this.netView = netView;
        }

        public void run(TaskMonitor monitor) throws Exception
        {
            final CyNetwork net = netView.getModel();
            final CyNode node = nodeView.getModel();
            collapseNode(net, node);
            applyLayout(netView, monitor);
        }

        public void cancel() { }
    }
}

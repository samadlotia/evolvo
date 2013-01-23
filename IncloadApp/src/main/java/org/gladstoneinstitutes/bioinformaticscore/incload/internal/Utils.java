
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

class Utils {
    public static void applyLayout(final CyNetworkView netView, final String layoutName, final TaskIterator iterator) {
        final CyLayoutAlgorithm alg = CyActivator.layoutMgr.getLayout(layoutName);
        final Set<View<CyNode>> nodes = new HashSet<View<CyNode>>(netView.getNodeViews());
        final TaskIterator tasks = alg.createTaskIterator(netView, alg.getDefaultLayoutContext(), nodes, null);
        iterator.append(tasks);
        iterator.append(new Task() {
            public void run(TaskMonitor monitor) {
                CyActivator.vizMapMgr.getCurrentVisualStyle().apply(netView);
            }
            public void cancel() {}
        });
    }

    public static CyNetwork newNetwork(final String name) {
        final CyNetwork net = CyActivator.netFct.createNetwork();
        net.getDefaultNetworkTable().getRow(net.getSUID()).set(CyNetwork.NAME, name);
        CyActivator.netMgr.addNetwork(net);
        return net;
    }

    public static CyNetworkView newNetworkView(final CyNetwork net) {
        final CyNetworkView netView = CyActivator.netViewFct.createNetworkView(net);
        CyActivator.netViewMgr.addNetworkView(netView);
        return netView;
    }

    public static CyNode newNode(final CyNetwork net, final String name) {
        final CyNode node = net.addNode();
        net.getDefaultNodeTable().getRow(node.getSUID()).set("name", name);
        return node;
    }


    /**
     * Get all the nodes with a given attribute value.
     *
     * This method is effectively a wrapper around {@link CyTable#getMatchingRows}.
     * It converts the table's primary keys (assuming they are node SUIDs) back to
     * nodes in the network.
     *
     * Here is an example of using this method to find all nodes with a given name:
     *
     * {@code
     *   CyNetwork net = ...;
     *   String nodeNameToSearchFor = ...;
     *   Set<CyNode> nodes = getNodesWithValue(net, net.getDefaultNodeTable(), "name", nodeNameToSearchFor);
     *   // nodes now contains all CyNodes with the name specified by nodeNameToSearchFor
     * }
     * @param net The network that contains the nodes you are looking for.
     * @param table The node table that has the attribute value you are looking for;
     * the primary keys of this table <i>must</i> be SUIDs of nodes in {@code net}.
     * @param colname The name of the column with the attribute value
     * @param value The attribute value
     * @return A set of {@code CyNode}s with a matching value, or an empty set if no nodes match.
     */
    public static Set<CyNode> getNodesWithValue(
            final CyNetwork net, final CyTable table,
            final String colname, final Object value) {
        final Collection<CyRow> matchingRows = table.getMatchingRows(colname, value);
        final Set<CyNode> nodes = new HashSet<CyNode>();
        final String primaryKeyColname = table.getPrimaryKey().getName();
        for (final CyRow row : matchingRows) {
            final Long nodeId = row.get(primaryKeyColname, Long.class);
            if (nodeId == null)
                continue;
            final CyNode node = net.getNode(nodeId);
            if (node == null)
                continue;
            nodes.add(node);
        }
        return nodes;
    }

    public static CyNode getNodeWithValue(
            final CyNetwork net, final CyTable table,
            final String colname, final Object value) {
        final Set<CyNode> nodes = getNodesWithValue(net, table, colname, value);
        if (nodes.size() == 0)
            return null;
        return nodes.iterator().next();
    }

    public static CyNode getNodeWithName(final CyNetwork net, final String name) {
        return getNodeWithValue(net, net.getDefaultNodeTable(), "name", name);
    }

    public static String getNodeName(final CyNetwork net, final CyNode node) {
        return net.getDefaultNodeTable().getRow(node.getSUID()).get("name", String.class);
    }
}

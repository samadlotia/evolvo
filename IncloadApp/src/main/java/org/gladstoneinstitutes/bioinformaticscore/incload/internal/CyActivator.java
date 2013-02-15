package org.gladstoneinstitutes.bioinformaticscore.incload.internal;

import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.Writer;
import java.io.Reader;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONWriter;
import org.json.JSONStringer;
import org.json.JSONException;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

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
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
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

import static org.gladstoneinstitutes.bioinformaticscore.incload.internal.Attr.*;

public class CyActivator extends AbstractCyActivator {
    static final Logger logger = LoggerFactory.getLogger("CyUserMessages");

    public static CyNetworkFactory netFct = null;
    public static CyNetworkManager netMgr = null;
    public static CyNetworkViewFactory netViewFct = null;
    public static CyNetworkViewManager netViewMgr = null;
    public static CyGroupFactory grpFct = null;
    public static CyGroupManager grpMgr = null;
    public static CyLayoutAlgorithmManager layoutMgr = null;
    public static CyEventHelper eventHelper = null;
    public static VisualMappingManager vizMapMgr = null;

    public CyActivator() {
        super();
    }

    private static Properties ezProps(String... vals) {
        final Properties props = new Properties();
        for (int i = 0; i < vals.length; i += 2)
            props.put(vals[i], vals[i + 1]);
        return props;
    }

    public void start(BundleContext bc) {
        netFct = getService(bc, CyNetworkFactory.class);
        netMgr = getService(bc, CyNetworkManager.class);
        netViewFct = getService(bc, CyNetworkViewFactory.class);
        netViewMgr = getService(bc, CyNetworkViewManager.class);
        grpFct = getService(bc, CyGroupFactory.class);
        grpMgr = getService(bc, CyGroupManager.class);
        layoutMgr = getService(bc, CyLayoutAlgorithmManager.class);
        eventHelper = getService(bc, CyEventHelper.class);
        vizMapMgr = getService(bc, VisualMappingManager.class);

        registerService(bc, new TaskFactory() {
            public TaskIterator createTaskIterator() {
                return new TaskIterator(new LoadNetworkTask());
            }

            public boolean isReady() {
                return true;
            }
        }, TaskFactory.class, ezProps(
            TITLE, "Incload",
            PREFERRED_MENU, "Apps"
        ));

        registerService(bc, new NodeViewTaskFactory() {
            public TaskIterator createTaskIterator(View<CyNode> nodeView, CyNetworkView netView) {
                return new TaskIterator(new ExpandTask(nodeView, netView));
            }

            public boolean isReady(View<CyNode> nodeView, CyNetworkView netView) {
                return isExpandable(netView.getModel(), nodeView.getModel());
            }

        }, NodeViewTaskFactory.class, ezProps(
            TITLE, "Incload: Expand",
            PREFERRED_MENU, "Apps"
        ));
    }

    public static class LoadNetworkTask implements Task {
        @Tunable(description="URL")
        public String url = "http://localhost:8000/expand_and_replace";

        public void run(final TaskMonitor monitor) throws Exception {
            monitor.setTitle("Incload: Opening network");
            monitor.setStatusMessage("Downloading from " + url);

            final CyNetwork net = Utils.newNetwork(String.format("%s (Incremental Network)", url));
            final URLConnection urlconn = (new URL(url)).openConnection();

            Attr(net, "IncloadURL").set(url);
            JSONObject serviceParams = new JSONObject(new JSONTokener(urlconn.getHeaderField("Incload-Info")));
            Attr(net, "IncloadInput").set(serviceParams.optString("input"));
            Attr(net, "IncloadAction").set(serviceParams.optString("action"));
            Attr(net, "IncloadNodeColumn").set(serviceParams.optString("node-column"));

            final InputStream input = urlconn.getInputStream();
            final JSONObject jInput = new JSONObject(new JSONTokener(new BufferedReader(new InputStreamReader(input))));

            JSONNetworkReader.read(jInput, net);
            eventHelper.flushPayloadEvents();
            final CyNetworkView netView = Utils.newNetworkView(net);
            layout(netView);
        }

        public void cancel() {}
    }

    private static boolean isExpandable(final CyNetwork net, final CyNode node) {
        //System.out.println(String.format("Node: %s, expandable?: %s", Attr(net, node, "name"), Attr(net, node, "expandable?")));
        return Attr(net, node, "expandable?").Bool(false);
    }

    private static void layout(final CyNetworkView netView) {
        final CyNetwork net = netView.getModel();
        for (final CyNode node : net.getNodeList()) {
            final View<CyNode> nodeView = netView.getNodeView(node);
            final CyRow row = net.getRow(node);
            final Number x = row.get("x", Number.class);
            final Number y = row.get("y", Number.class);
            if (x != null) nodeView.setVisualProperty(BasicVisualLexicon.NODE_X_LOCATION, x.doubleValue());
            if (y != null) nodeView.setVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION, y.doubleValue());
        }
	vizMapMgr.getCurrentVisualStyle().apply(netView);
    }

    private static void writeRequest(final Writer writer, final CyNode nodeToExpand, final CyNetwork net) throws JSONException {
        final String column = Attr(net, "IncloadNodeColumn").Str();
        final JSONWriter output = new JSONWriter(writer);
        output.object();
        output.key("node").value(net.getRow(nodeToExpand).getRaw(column).toString());
        output.key("expanded-nodes").array();
        for (final CyNode node : net.getNodeList()) {
            if (node.equals(nodeToExpand))
                continue;
            output.value(net.getRow(node).getRaw(column).toString());
        }
        output.endArray().endObject();
    }

    private static class ExpandTask implements Task {
        final View<CyNode> nodeView;
        final CyNetworkView netView;

        public ExpandTask(View<CyNode> nodeView, CyNetworkView netView) {
            this.nodeView = nodeView;
            this.netView = netView;
        }

        public void run(final TaskMonitor monitor) throws Exception {
            final CyNetwork net = netView.getModel();
            final CyNode node = nodeView.getModel();

            final String url = Attr(net, "IncloadURL").Str();
            final HttpURLConnection urlconn = (HttpURLConnection) (new URL(url)).openConnection();
            urlconn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            urlconn.setDoOutput(true);
            urlconn.setDoInput(true);
            urlconn.connect();

            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(urlconn.getOutputStream()));
            writeRequest(writer, node, net);
            writer.close();

            final Reader reader = new InputStreamReader(urlconn.getInputStream());
            final JSONObject jInput = new JSONObject(new JSONTokener(reader));
            reader.close();

            //final CyRootNetwork rootNet = ((CySubNetwork) net).getRootNetwork();
            final String expandOnNodeAttr = Attr(net, "IncloadNodeColumn").Str();
            final JSONNetworkReader.Result result = JSONNetworkReader.read(jInput, net);
            eventHelper.flushPayloadEvents();

            //printNodes("nodes:", net, result.nodes);
            //printNodes("new nodes:", net, result.newNodes);

            final CyGroup group = grpFct.createGroup(net, node, result.newNodes, result.newEdges, true);
            group.collapse(net);
            eventHelper.flushPayloadEvents();

            dumpGroup(net, group);
        }

        public void cancel() {}
    }

    private static void printNodes(final String prefix, final CyNetwork net, List<CyNode> nodes) {
        System.out.print(prefix);
        System.out.print(' ');
        for (final CyNode node : nodes) {
            System.out.print(Attr(net, node, "name").Str());
            System.out.print(" ");
        }
        System.out.println();
    }

    private static void dumpGroup(final CyNetwork net, final CyGroup grp) {
        System.out.println("All Nodes:");
        for (final CyNode node : net.getNodeList()) {
            System.out.println(String.format("  %d - %s", node.getSUID(), Attr(net, node, "name")));
        }

        System.out.println("Nodes:");
        for (final CyNode node : grp.getNodeList()) {
            System.out.println(String.format("  %d - %s", node.getSUID(), Attr(net, node, "name")));
        }

        System.out.println("Int edges:");
        for (final CyEdge edge : grp.getInternalEdgeList()) {
            System.out.println(String.format(" %d - %s <-> %s", edge.getSUID(), Attr(net, edge.getSource(), "name"), Attr(net, edge.getTarget(), "name")));
        }

        System.out.println("Ext edges:");
        for (final CyEdge edge : grp.getExternalEdgeList()) {
            System.out.println(String.format("  %d - %s <-> %s", edge.getSUID(), Attr(net, edge.getSource(), "name"), Attr(net, edge.getTarget(), "name")));
        }
    }
}

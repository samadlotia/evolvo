package EvolvoApp.internal;

import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.Writer;
import java.io.Reader;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
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

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;

import static EvolvoApp.internal.Attr.*;

import EvolvoApp.internal.json.JsonNetworkReader;
import EvolvoApp.internal.json.InvalidJsonException;

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

    private static enum EvolvoAction {
        REPLACE,
        AUGMENT;

        public static EvolvoAction get(final CyNetwork net) {
            final String actionName = Attr(net, "Evolvo-action").Str();
            for (final EvolvoAction action : EvolvoAction.values())
                if (actionName.equalsIgnoreCase(action.name()))
                    return action;
            return null;
        }
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
                TaskIterator taskIterator = new TaskIterator();
                taskIterator.append(new OpenNetworkTask(taskIterator));
                return taskIterator;
            }

            public boolean isReady() {
                return true;
            }
        }, TaskFactory.class, ezProps(
            TITLE, "Evolvo: Open network",
            PREFERRED_MENU, "Apps"
        ));

        registerService(bc, new NodeViewTaskFactory() {
            public TaskIterator createTaskIterator(View<CyNode> nodeView, CyNetworkView netView) {
                final TaskIterator taskIterator = new TaskIterator();
                if (EvolvoAction.get(netView.getModel()).equals(EvolvoAction.REPLACE)) {
                    taskIterator.append(new ReplaceExpandTask(nodeView, netView));
                } else {
                    taskIterator.append(new AugmentExpandTask(nodeView, netView));
                }
                taskIterator.append(new LayoutTask(netView, taskIterator));
                return taskIterator;
            }

            public boolean isReady(View<CyNode> nodeView, CyNetworkView netView) {
                return isExpandable(netView.getModel(), nodeView.getModel());
            }

        }, NodeViewTaskFactory.class, ezProps(
            TITLE, "Evolvo: Expand",
            PREFERRED_MENU, "Apps"
        ));

        registerService(bc, new NodeViewTaskFactory() {
            public TaskIterator createTaskIterator(View<CyNode> nodeView, CyNetworkView netView) {
                final TaskIterator taskIterator = new TaskIterator();
                if (EvolvoAction.get(netView.getModel()).equals(EvolvoAction.REPLACE)) {
                    taskIterator.append(new ReplaceCollapseTask(nodeView, netView, true));
                } else {
                    taskIterator.append(new AugmentCollapseTask(nodeView, netView, true));
                }
                taskIterator.append(new LayoutTask(netView, taskIterator));
                return taskIterator;
            }

            public boolean isReady(View<CyNode> nodeView, CyNetworkView netView) {
                return isCollapsable(netView.getModel(), nodeView.getModel());
            }

        }, NodeViewTaskFactory.class, ezProps(
            TITLE, "Evolvo: Collapse & Clear",
            PREFERRED_MENU, "Apps"
        ));

        registerService(bc, new NodeViewTaskFactory() {
            public TaskIterator createTaskIterator(View<CyNode> nodeView, CyNetworkView netView) {
                final TaskIterator taskIterator = new TaskIterator();
                if (EvolvoAction.get(netView.getModel()).equals(EvolvoAction.REPLACE)) {
                    taskIterator.append(new ReplaceCollapseTask(nodeView, netView, false));
                } else {
                    taskIterator.append(new AugmentCollapseTask(nodeView, netView, false));
                }
                taskIterator.append(new LayoutTask(netView, taskIterator));
                return taskIterator;
            }

            public boolean isReady(View<CyNode> nodeView, CyNetworkView netView) {
                return isCollapsable(netView.getModel(), nodeView.getModel());
            }

        }, NodeViewTaskFactory.class, ezProps(
            TITLE, "Evolvo: Collapse",
            PREFERRED_MENU, "Apps"
        ));
    }

    private static final JsonFactory jsonFactory = new JsonFactory();

    public static class OpenNetworkTask implements Task {
        final TaskIterator taskIterator;

        public OpenNetworkTask(final TaskIterator taskIterator) {
            this.taskIterator = taskIterator;
        }

        @Tunable(description="URL")
        public String url = "http://localhost:8000/augment";

        public void run(final TaskMonitor monitor) throws Exception {
            final URLConnection urlconn = (new URL(url)).openConnection();

            monitor.setTitle("Evolvo: Opening network");
            monitor.setStatusMessage(url);

            final CyNetwork net = Utils.newNetwork(String.format("Evolvo: %s", url));

            Attr(net, "Evolvo-url").set(url);
            Attr(net, "Evolvo-action").set(urlconn.getHeaderField("Evolvo-action"));
            Attr(net, "Evolvo-node-column").set(urlconn.getHeaderField("Evolvo-node-column"));
            net.getDefaultNetworkTable().createListColumn("Evolvo-hidden-parents", Long.class, false, new ArrayList<Long>());

            final InputStream input = urlconn.getInputStream();
            final JsonParser jsonParser = jsonFactory.createJsonParser(input);
            JsonNetworkReader.read(jsonParser, net);
            input.close();

            eventHelper.flushPayloadEvents();
            final CyNetworkView netView = Utils.newNetworkView(net);
            taskIterator.append(new LayoutTask(netView, taskIterator));

            System.out.println();
            System.out.println("OpenNetworkTask");
            dumpNet(net);
        }

        public void cancel() {}
    }

    private static boolean isExpandable(final CyNetwork net, final CyNode node) {
        boolean expandable = true;
        if (net.getDefaultNodeTable().getColumn("expandable") != null) {
            expandable = Attr(net, node, "expandable").Bool(false);
        }
        final boolean expanded = Attr(net, node, "Evolvo-expanded").Bool(false);
        return (expandable && !expanded);
    }

    private static boolean isCollapsable(final CyNetwork net, final CyNode node) {
        if (EvolvoAction.get(net).equals(EvolvoAction.REPLACE)) {
            return Attr(net, node, "Evolvo-parent").Long() != null;
        } else {
            return Attr(net, node, "Evolvo-expanded").Bool(false);
        }
    }

    private static class LayoutTask implements Task {
        final CyNetworkView netView;
        final TaskIterator taskIterator;
        public LayoutTask(final CyNetworkView netView, final TaskIterator taskIterator) {
            this.netView = netView;
            this.taskIterator = taskIterator;
        }

        public void run(TaskMonitor monitor) {
            final CyNetwork net = netView.getModel();
            final CyTable nodeTable = net.getDefaultNodeTable();
            if (nodeTable.getColumn("x") == null || nodeTable.getColumn("y") == null) {
                Utils.applyLayout(netView, "hierarchical", taskIterator);
            } else {
                for (final CyNode node : net.getNodeList()) {
                    final View<CyNode> nodeView = netView.getNodeView(node);
                    final CyRow row = nodeTable.getRow(node.getSUID());
                    final Number x = row.get("x", Number.class);
                    final Number y = row.get("y", Number.class);
                    if (x != null && y != null) {
                        nodeView.setVisualProperty(BasicVisualLexicon.NODE_X_LOCATION, x.doubleValue());
                        nodeView.setVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION, y.doubleValue());
                    }
                }
            }
            vizMapMgr.getCurrentVisualStyle().apply(netView);
        }

        public void cancel() {}
    }

    private static void writeRequest(
            final Writer writer,
            final CyNode nodeToExpand,
            final CyNetwork net,
            final CyTable nodeTable,
            final boolean includeExtantNodes
            ) throws IOException, JsonGenerationException {

        final String column = Attr(net, "Evolvo-node-column").Str();
        final JsonGenerator output = jsonFactory.createJsonGenerator(writer);
        output.writeStartObject();
        output.writeStringField("target", net.getRow(nodeToExpand).getRaw(column).toString());
        if (includeExtantNodes) {
            output.writeFieldName("extant-nodes");
            output.writeStartArray();
            for (final CyNode node : net.getNodeList()) {
                if (node.equals(nodeToExpand))
                    continue;
                output.writeString(nodeTable.getRow(node.getSUID()).getRaw(column).toString());
            }
            for (final Long hiddenParentSUID : net.getRow(net).getList("Evolvo-hidden-parents", Long.class)) {
                output.writeString(nodeTable.getRow(hiddenParentSUID).getRaw(column).toString());
            }
            output.writeEndArray();
        }
        output.writeEndObject();
        output.close();
    }

    private static void expandFromRootNetwork(final CyNetwork net, final CyNode node) {
        final CySubNetwork subnet = (CySubNetwork) net;
        final CyRootNetwork rootnet = subnet.getRootNetwork();
        final Set<CyNode> children = Utils.getNodesWithValue(rootnet, net.getDefaultNodeTable(), "Evolvo-parent", node.getSUID());
        
        for (final CyNode child : children)
            subnet.addNode(child);

        for (final CyNode child : children)
            for (final CyEdge edge : rootnet.getAdjacentEdgeIterable(child, CyEdge.Type.ANY))
                if (subnet.containsNode(edge.getSource()) && subnet.containsNode(edge.getTarget()))
                    subnet.addEdge(edge);
    }

    private static void expandFromURL(final CyNetwork net, final CyNode node) throws MalformedURLException, IOException, JsonParseException, JsonGenerationException, InvalidJsonException {
        final CySubNetwork  subnet  = (CySubNetwork) net;
        final CyRootNetwork rootnet = subnet.getRootNetwork();

        final String url = Attr(net, "Evolvo-url").Str();
        final HttpURLConnection urlconn = (HttpURLConnection) (new URL(url)).openConnection();
        urlconn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        urlconn.setDoOutput(true);
        urlconn.setDoInput(true);
        urlconn.connect();

        final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(urlconn.getOutputStream()));
        writeRequest(writer, node, net, net.getDefaultNodeTable(), true);
        writer.close();

        final Reader reader = new InputStreamReader(urlconn.getInputStream());
        final JsonParser jsonParser = jsonFactory.createJsonParser(reader);
        JsonNetworkReader.read(jsonParser, net,
                new JsonNetworkReader.NonDuplicatingNodeFactory(
                    new JsonNetworkReader.BasicNodeFactory(net) {
                        public CyNode create(Object[] row, Class[] types) {
                            final CyNode childNode = super.create(row, types);
                            Attr(net, childNode, "Evolvo-parent").set(node.getSUID());
                            return childNode;
                        }
                    },
                    rootnet,
                    net.getDefaultNodeTable(),
                    Attr(net, "Evolvo-node-column").Str()),
                new JsonNetworkReader.NodeAttrHandler(net),
                new JsonNetworkReader.BasicEdgeFactory(rootnet, false, false) {
                    public CyEdge create(Object[] row, Class[] types) throws InvalidJsonException {
                        final CyEdge edge = super.create(row, types);
                        if (subnet.containsNode(edge.getSource()) && subnet.containsNode(edge.getTarget()))
                            subnet.addEdge(edge);
                        return edge;
                    }
                },
                new JsonNetworkReader.EdgeAttrHandler(net),
                new JsonNetworkReader.NetworkAttrHandler(net));
        reader.close();
    }

    private static void addToHiddenParents(final CyNetwork net, final CyNode parentNode) {
        final CyRow netRow = net.getRow(net);
        final List<Long> hiddenParents = netRow.getList("Evolvo-hidden-parents", Long.class);
        hiddenParents.add(parentNode.getSUID());
        System.out.println("removeFromHiddenParents: " + netRow.getList("Evolvo-hidden-parents", Long.class));
    }

    private static void removeFromHiddenParents(final CyNetwork net, final CyNode parentNode) {
        final CyRow netRow = net.getDefaultNetworkTable().getRow(net.getSUID());
        final List<Long> hiddenParents = netRow.getList("Evolvo-hidden-parents", Long.class);
        hiddenParents.remove(parentNode.getSUID());
        System.out.println("addToHiddenParents: " + netRow.getList("Evolvo-hidden-parents", Long.class));
    }

    private static class ReplaceExpandTask implements Task {
        final View<CyNode> nodeView;
        final CyNetworkView netView;

        public ReplaceExpandTask(View<CyNode> nodeView, CyNetworkView netView) {
            this.nodeView = nodeView;
            this.netView = netView;
        }

        public void run(final TaskMonitor monitor) throws Exception {
            final CyNetwork     net     = netView.getModel();
            final CySubNetwork  subnet  = (CySubNetwork) net;
            final CyRootNetwork rootnet = subnet.getRootNetwork();
            final CyNode        node    = nodeView.getModel();

            final Set<CyNode> children = Utils.getNodesWithValue(rootnet, net.getDefaultNodeTable(), "Evolvo-parent", node.getSUID());
            if (children.size() == 0)
                expandFromURL(net, node);
            else
                expandFromRootNetwork(net, node);

            Attr(net, node, "Evolvo-expanded").set(true);

            net.removeEdges(net.getAdjacentEdgeList(node, CyEdge.Type.ANY));
            net.removeNodes(Collections.singleton(node));
            addToHiddenParents(net, node);

            eventHelper.flushPayloadEvents();

            System.out.println();
            System.out.println("ReplaceExpandTask: " + children.size());
            dumpNet(net);
        }

        public void cancel() {}
    }

    private static class AugmentExpandTask implements Task {
        final View<CyNode> nodeView;
        final CyNetworkView netView;

        public AugmentExpandTask(View<CyNode> nodeView, CyNetworkView netView) {
            this.nodeView = nodeView;
            this.netView = netView;
        }

        public void run(final TaskMonitor monitor) throws Exception {
            final CyNetwork     net     = netView.getModel();
            final CySubNetwork  subnet  = (CySubNetwork) net;
            final CyRootNetwork rootnet = subnet.getRootNetwork();
            final CyNode        node    = nodeView.getModel();

            final Set<CyNode> children = Utils.getNodesWithValue(rootnet, net.getDefaultNodeTable(), "Evolvo-parent", node.getSUID());
            if (children.size() == 0)
                expandFromURL(net, node);
            else
                expandFromRootNetwork(net, node);

            Attr(net, node, "Evolvo-expanded").set(true);

            eventHelper.flushPayloadEvents();

            System.out.println();
            System.out.println("ReplaceExpandTask: " + children.size());
            dumpNet(net);
        }

        public void cancel() {}
    }

    private static class ReplaceCollapseTask implements Task {
        final View<CyNode> nodeView;
        final CyNetworkView netView;
        final boolean clear;

        public ReplaceCollapseTask(View<CyNode> nodeView, CyNetworkView netView, boolean clear) {
            this.nodeView = nodeView;
            this.netView = netView;
            this.clear = clear;
        }

        public void run(final TaskMonitor monitor) throws Exception {
            final CyNetwork     net     = netView.getModel();
            final CySubNetwork  subnet  = (CySubNetwork) net;
            final CyRootNetwork rootnet = subnet.getRootNetwork();
            final CyTable       nodetbl = net.getDefaultNodeTable();

            final Long parentSUID = Attr(net, nodeView.getModel(), "Evolvo-parent").Long();
            final Set<CyNode> siblings = Utils.getNodesWithValue(net, nodetbl, "Evolvo-parent", parentSUID);
            final Set<Long> siblingSUIDs = Utils.toSUIDs(siblings);

            // delete the nodes from subnetwork
            subnet.removeNodes(siblings);

            if (clear) {
                // delete all table info
                nodetbl.deleteRows(siblingSUIDs);
                rootnet.removeNodes(siblings);
            }

            final CyNode parentNode = rootnet.getNode(parentSUID);

            // add the parent from the root network back into the subnetwork
            subnet.addNode(parentNode);

            // add parent node's edges back into subnetwork
            for (final CyEdge edge : rootnet.getAdjacentEdgeIterable(parentNode, CyEdge.Type.ANY))
                if (subnet.containsNode(edge.getSource()) && subnet.containsNode(edge.getTarget()))
                    subnet.addEdge(edge);

            removeFromHiddenParents(net, parentNode);

            Attr(net, parentNode, "Evolvo-expanded").set(false);

            eventHelper.flushPayloadEvents();

            System.out.println();
            System.out.println("ReplaceCollapseTask");
            dumpNet(net);
        }

        public void cancel() {}
    }

    private static class AugmentCollapseTask implements Task {
        final View<CyNode> nodeView;
        final CyNetworkView netView;
        final boolean clear;

        public AugmentCollapseTask(View<CyNode> nodeView, CyNetworkView netView, boolean clear) {
            this.nodeView = nodeView;
            this.netView = netView;
            this.clear = clear;
        }

        public void run(final TaskMonitor monitor) throws Exception {
            final CyNetwork     net     = netView.getModel();
            final CySubNetwork  subnet  = (CySubNetwork) net;
            final CyRootNetwork rootnet = subnet.getRootNetwork();
            final CyTable       nodetbl = net.getDefaultNodeTable();

            final CyNode parentNode = nodeView.getModel();
            final Long parentSUID = parentNode.getSUID();
            final Set<CyNode> children = Utils.getNodesWithValue(net, nodetbl, "Evolvo-parent", parentSUID);

            // delete the nodes from subnetwork
            subnet.removeNodes(children);

            if (clear) {
                // delete all table info
                nodetbl.deleteRows(Utils.toSUIDs(children));
                rootnet.removeNodes(children);
            }

            Attr(net, parentNode, "Evolvo-expanded").set(false);

            eventHelper.flushPayloadEvents();

            System.out.println();
            System.out.println("AugmentCollapseTask");
            dumpNet(net);
        }

        public void cancel() {}
    }

    private static void dumpNet(final CyNetwork subnet) {
        final CyRootNetwork net = ((CySubNetwork) subnet).getRootNetwork();
        final CyTable table = subnet.getDefaultNodeTable();
        System.out.println("Nodes:");
        for (final CyNode node : net.getNodeList()) {
            System.out.println(String.format(
                        "\t%s%c (%d)",
                        table.getRow(node.getSUID()).get("shared name", String.class),
                        subnet.containsNode(node) ? '*' : ' ',
                        node.getSUID()));
        }

        System.out.println("Edges:");
        for (final CyEdge edge : net.getEdgeList()) {
            final CyNode src = edge.getSource();
            final CyNode trg = edge.getTarget();
            System.out.println(String.format(
                        "\t%d:\t%s (%d) ->\t%s (%d)%c",
                        edge.getSUID(),
                        table.getRow(src.getSUID()).get("shared name", String.class),
                        src.getSUID(),
                        table.getRow(trg.getSUID()).get("shared name", String.class),
                        trg.getSUID(),
                        subnet.containsEdge(edge) ? '*' : ' '));
        }
    }

    /*
    private static void printNodes(final String prefix, final CyNetwork net, Iterable<CyNode> nodes) {
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
    */
}

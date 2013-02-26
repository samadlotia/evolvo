package EvolvoApp.internal;

import java.util.Map;
import java.util.HashMap;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyColumn;

/**
 * Builder class for working with network object attributes.
 * It is best to statically import the {@code Attr} methods rather than importing this class.
 *
 * <p>Using this class is best explained through examples.</p>
 * 
 * <h4>Getting attributes from the default table</h4>
 *
 * <p>
 * Get the "name" attribute of a node as a string.
 * <blockquote>
 *   {@code Attr(network, node, "name").Str()}
 * </blockquote>
 * </p>
 *
 * <p>
 * Get the "isColorRed?" attribute of a node as a boolean.
 * <blockquote>
 *   {@code Attr(network, node, "isColorRed?").Bool()}
 * </blockquote>
 * </p>
 *
 * <p>
 * <i>Default value:</i>
 * Get the "weight" attribute of an as an integer.
 * If the edge does not have this attribute, return the default value of 30.
 * <blockquote>
 *   {@code Attr(network, edge, "nodeSize").Int(30)}
 * </blockquote>
 * </p>
 *
 * <h4>Setting attributes from to default table</h4>
 * If the column does not exist, {@code Attr} will create one
 * with type of the value given. If the column exists but
 * the value's type does not match, this will throw an exception.
 *
 * <p>
 * Set the "isColorRed?" attribute of a node as a string.
 * <blockquote>
 *   {@code Attr(network, node, "isColorRed?").set(false);}
 * </blockquote>
 * </p>
 *
 * <p>
 * Set the name of a network.
 * <blockquote>
 *   {@code Attr(network, CyNetwork.NAME).set("super cool network")}
 * </blockquote>
 * </p>
 *
 * <h4>Getting and setting attributes from tables besides the default</h4>
 *
 * <p>
 * <i>Hidden table:</i>
 * Set the "name" attribute of a node as a string in the hidden table.
 * <blockquote>
 *   {@code Attr(network, node, "name").hidden().set("my nodes name is hidden");}
 * </blockquote>
 * </p>
 *
 * <p>
 * <i>Local table:</i>
 * Get the "weight" attribute of an edge as an integer with
 * the default value of 15 from the local table.
 * <blockquote>
 *   {@code Attr(network, edge, "weight").local().Int(15)}
 * </blockquote>
 * </p>
 *
 * <p>
 * <i>Shared table:</i>
 * Set the "isColorRed?" attribute of a node from the shared table.
 * Note that the root network will be derived from the given network
 * to obtain the shared table.
 * <blockquote>
 *   {@code Attr(network, node, "isColorRed?").shared().set(true)}
 * </blockquote>
 * </p>
 */
public class Attr {
    CyNetwork net          = null;
    CyRootNetwork rootNet  = null;
    CyIdentifiable netObj  = null;
    Class<? extends CyIdentifiable> netObjType = null;
    String ns              = CyNetwork.DEFAULT_ATTRS;
    String column          = null;

    public static Attr Attr() {
        return new Attr();
    }

    /**
     * Obtain a network attribute for a given column.
     */
    public static Attr Attr(final CyNetwork net, final String column) {
        return Attr().on(net, column);
    }

    /**
     * Obtain a node attribute for a given column.
     */
    public static Attr Attr(final CyNetwork net, final CyNode node, final String column) {
        return Attr().on(net, node, column);
    }

    /**
     * Obtain a node attribute for a given column.
     */
    public static Attr Attr(final CyNetwork net, final CyEdge edge, final String column) {
        return Attr().on(net, edge, column);
    }

    /**
     * Use one of the static methods above to create an instance.
     */
    private Attr() {}

    // -------------------------------------------
    
    /**
     * Assign a different network from which to obtain attributes.
     * This method should be called only when working with node or edge
     * attributes. This method should not be used when obtaining network
     * attributes.
     */
    public Attr net(final CyNetwork net) {
        this.net = net;
        return this;
    }

    /**
     * Work with network attributes.
     * This does not assign a column.
     */
    public Attr on(final CyNetwork net) {
        this.net = net;
        this.netObj = net;
        this.netObjType = CyNetwork.class;
        return this;
    }

    /**
     * Work with network attributes for the given column.
     */
    public Attr on(final CyNetwork net, final String column) {
        this.net = net;
        this.netObj = net;
        this.netObjType = CyNetwork.class;
        this.column = column;
        return this;
    }

    /**
     * Work with node attributes.
     * This does not assign a network or a column.
     */
    public Attr on(final CyNode node) {
        this.netObj = node;
        this.netObjType = CyNode.class;
        return this;
    }

    /**
     * Work with node attributes.
     * This does not assign a column.
     */
    public Attr on(final CyNetwork net, final CyNode node) {
        this.net = net;
        this.netObj = node;
        this.netObjType = CyNode.class;
        return this;
    }

    /**
     * Work with node attributes.
     * This does not assign a network.
     */
    public Attr on(final CyNode node, final String column) {
        this.netObj = node;
        this.netObjType = CyNode.class;
        this.column = column;
        return this;
    }

    /**
     * Work with node attributes.
     */
    public Attr on(final CyNetwork net, final CyNode node, final String column) {
        this.net = net;
        this.netObj = node;
        this.netObjType = CyNode.class;
        this.column = column;
        return this;
    }

    public Attr on(final CyEdge edge) {
        this.netObj = edge;
        this.netObjType = CyEdge.class;
        return this;
    }

    public Attr on(final CyNetwork net, final CyEdge edge) {
        this.net = net;
        this.netObj = edge;
        this.netObjType = CyEdge.class;
        return this;
    }

    public Attr on(final CyEdge edge, final String column) {
        this.netObj = edge;
        this.netObjType = CyEdge.class;
        this.column = column;
        return this;
    }

    public Attr on(final CyNetwork net, final CyEdge edge, final String column) {
        this.net = net;
        this.netObj = edge;
        this.netObjType = CyEdge.class;
        this.column = column;
        return this;
    }

    public Attr col(final String column) {
        this.column = column;
        return this;
    }

    // -------------------------------------------

    /**
     * Use the hidden table.
     */
    public Attr hidden() {
        ns = CyNetwork.HIDDEN_ATTRS;
        return this;
    }

    /**
     * Use the local table.
     */
    public Attr local() {
        ns = CyNetwork.LOCAL_ATTRS;
        return this;
    }

    /**
     * Use the shared table.
     */
    public Attr shared() {
        ns = CyRootNetwork.SHARED_ATTRS;
        net = ((CySubNetwork) net).getRootNetwork();
        return this;
    }

    // -------------------------------------------

    private CyTable table() {
        return net.getTable(netObjType, ns);
    }

    private CyRow row() {
        return table().getRow(netObj.getSUID());
    }

    // -------------------------------------------
    
    private <T> void ensureColumn(final Class<? extends T> type) {
        final CyTable table = table();
        final CyColumn col = table.getColumn(column);
        if (col == null) {
            table.createColumn(column, type, false);
        } else if (!col.getType().equals(type)) {
            throw new IllegalArgumentException(String.format("Column expects %s, but attempting to insert value of type %s", col.getType(), type));
        }
    }

    /**
     * Assign the attribute as a string.
     */
    public void set(String value) {
        ensureColumn(String.class);
        row().set(column, value);
    }

    /**
     * Assign the attribute as a boolean.
     */
    public void set(Boolean value) {
        ensureColumn(Boolean.class);
        row().set(column, value);
    }

    /**
     * Assign the attribute as an integer.
     */
    public void set(Integer value) {
        ensureColumn(Integer.class);
        row().set(column, value);
    }

    /**
     * Assign the attribute as a long.
     */
    public void set(Long value) {
        ensureColumn(Long.class);
        row().set(column, value);
    }

    // -------------------------------------------

    public String toString() {
        final Object val = row().getRaw(column);
        return val == null ? "null" : val.toString();
    }

    /**
     * Obtain the attribute as a string.
     * If there is no attribute for the given column,
     * this will return the default value.
     */
    public String Str(final String defaultValue) {
        final String val = row().get(column, String.class);
        return val != null ? val : defaultValue;
    }

    /**
     * Obtain the attribute as a string.
     */
    public String Str() {
        return Str(null);
    }

    /**
     * Obtain the attribute as a boolean.
     * If there is no attribute for the given column,
     * this will return the default value.
     */
    public Boolean Bool(final Boolean defaultValue) {
        final Boolean val = row().get(column, Boolean.class);
        return val != null ? val : defaultValue;
    }

    /**
     * Obtain the attribute as a boolean.
     */
    public Boolean Bool() {
        return Bool(null);
    }

    /**
     * Obtain the attribute as an integer.
     * If there is no attribute for the given column,
     * this will return the default value.
     */
    public Integer Int(final Integer defaultValue) {
        final Integer val = row().get(column, Integer.class);
        return val != null ? val : defaultValue;
    }

    /**
     * Obtain the attribute as a boolean.
     */
    public Integer Int() {
        return Int(null);
    }

    /**
     * Obtain the attribute as a long.
     * If there is no attribute for the given column,
     * this will return the default value.
     */
    public Long Long(final Long defaultValue) {
        final Long val = row().get(column, Long.class);
        return val != null ? val : defaultValue;
    }

    /**
     * Obtain the attribute as a boolean.
     */
    public Long Long() {
        return Long(null);
    }

    /**
     * Obtain the attribute as a double.
     * If there is no attribute for the given column,
     * this will return the default value.
     */
    public Double Double(final Double defaultValue) {
        final Double val = row().get(column, Double.class);
        return val != null ? val : defaultValue;
    }

    /**
     * Obtain the attribute as a boolean.
     */
    public Double Double() {
        return Double(null);
    }

    /**
     * Obtain the attribute as a number.
     * If there is no attribute for the given column,
     * this will return the default value.
     */
    public Number Number(final Number defaultValue) {
        final Number val = row().get(column, Number.class);
        return val != null ? val : defaultValue;
    }

    /**
     * Obtain the attribute as a boolean.
     */
    public Number Number() {
        return Number(null);
    }
}

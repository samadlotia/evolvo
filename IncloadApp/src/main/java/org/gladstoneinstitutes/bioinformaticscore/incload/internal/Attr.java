package org.gladstoneinstitutes.bioinformaticscore.incload.internal;

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
 *   {@code Attr(network, edge, "nodeSize").def(30).Int()}
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
 *   {@code Attr(network, edge, "weight").local().def(15).Int()}
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
    CyIdentifiable netObj  = null;
    Class<? extends CyIdentifiable> netObjType = null;
    String ns              = CyNetwork.DEFAULT_ATTRS;
    String column          = null;
    Object defaultValue    = null;

    /**
     * Obtain a network attribute for a given column.
     */
    public static Attr Attr(final CyNetwork net, final String column) {
        return new Attr(net, net, column);
    }

    /**
     * Obtain a network object attribute for a given column.
     */
    public static Attr Attr(final CyNetwork net, final CyIdentifiable netObj, final String column) {
        return new Attr(net, netObj, column);
    }

    /**
     * Use one of the static methods above to create an instance.
     */
    private Attr(final CyNetwork net, final CyIdentifiable netObj, final String column) {
        this.net = net;
        this.netObj = netObj;
        this.column = column;
        if (netObj instanceof CyNetwork)
            netObjType = CyNetwork.class;
        else if (netObj instanceof CyNode)
            netObjType = CyNode.class;
        else if (netObj instanceof CyEdge)
            netObjType = CyEdge.class;
        else
            throw new IllegalArgumentException("Unrecognized network object type -- must be a CyNetwork, CyNode, or CyEdge");
    }

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

    /**
     * Assign a default value.
     */
    public Attr def(final Object defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    private Object value() {
        final Object value = row().getRaw(column);
        return value == null ? defaultValue : value;
    }

    /**
     * Obtain the attribute as a string.
     */
    public String Str() {
        return (String) value();
    }

    /**
     * Obtain the attribute as an integer.
     */
    public Integer Int() {
        return (Integer) value();
    }

    /**
     * Obtain the attribute as a long.
     */
    public Long Long() {
        return (Long) value();
    }

    /**
     * Obtain the attribute as a boolean.
     */
    public Boolean Bool() {
        return (Boolean) value();
    }
}


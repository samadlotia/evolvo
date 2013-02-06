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

class Attr {
    CyNetwork net          = null;
    CyIdentifiable netObj  = null;
    Class<? extends CyIdentifiable> netObjType = null;
    String ns              = CyNetwork.DEFAULT_ATTRS;
    String column          = null;
    Object defaultValue    = null;

    public static Attr Attr(final CyNetwork net, final String column) {
        return new Attr(net, net, column);
    }

    public static Attr Attr(final CyNetwork net, final CyIdentifiable netObj, final String column) {
        return new Attr(net, netObj, column);
    }

    public Attr(final CyNetwork net, final CyIdentifiable netObj, final String column) {
        this.net = net;
        this.netObj = netObj;
        if (netObj instanceof CyNetwork)
            netObjType = CyNetwork.class;
        else if (netObj instanceof CyNode)
            netObjType = CyNode.class;
        else if (netObj instanceof CyEdge)
            netObjType = CyEdge.class;
        this.column = column;
    }

    public Attr hidden() {
        ns = CyNetwork.HIDDEN_ATTRS;
        return this;
    }

    public Attr local() {
        ns = CyNetwork.LOCAL_ATTRS;
        return this;
    }

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

    public void set(String value) {
        System.out.println(String.format("Net: %s, obj: %s, ns: %s, col: %s", net, netObj, ns, column));
        ensureColumn(String.class);
        row().set(column, value);
    }

    public void set(Boolean value) {
        ensureColumn(Boolean.class);
        row().set(column, value);
    }

    public void set(Integer value) {
        ensureColumn(Integer.class);
        row().set(column, value);
    }

    public void set(Long value) {
        ensureColumn(Long.class);
        row().set(column, value);
    }

    // -------------------------------------------

    public Attr def(final Object defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    private Object value() {
        final Object value = row().getRaw(column);
        return value == null ? defaultValue : value;
    }

    public String Str() {
        return (String) value();
    }

    public Integer Int() {
        return (Integer) value();
    }

    public Long Long() {
        return (Long) value();
    }

    public Boolean Bool() {
        return (Boolean) value();
    }
}


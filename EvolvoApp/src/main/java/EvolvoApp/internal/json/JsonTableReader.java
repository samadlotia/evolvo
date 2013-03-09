package EvolvoApp.internal.json;

import java.util.List;
import java.util.ArrayList;

import java.math.BigInteger;
import java.math.BigDecimal;

import java.io.IOException;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonLocation;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.JsonParseException;

/**
 * Reads a table specified in JSON.
 * This is completely independent of {@code CyTable}s and
 * should not be confused with it.
 */
class JsonTableReader {
    public static interface Delegate {
        public void header(String[] cols) throws InvalidJsonException;
        public void row(Object[] elems, Class[] types) throws InvalidJsonException;
        public void done() throws InvalidJsonException;
    }

    public static void read(final JsonParser p, final Delegate delegate) throws IOException, JsonParseException, InvalidJsonException {
        JsonToken t = null;

        t = p.nextToken(); // start of table
        if (t == null)
            throw new InvalidJsonException("unexpected end of output");
        else if (!t.equals(JsonToken.START_ARRAY)) // start of table array
            throw new InvalidJsonException("table must be an array");

        t = p.nextToken(); // start of header or end of table array
        if (t == null)
            throw new InvalidJsonException("unexpected end of output");
        else if (t.equals(JsonToken.END_ARRAY))
            return; // the table is just an empty array, so just exit
        else if (!t.equals(JsonToken.START_ARRAY))
            throw new InvalidJsonException("first element in table must be the header array");

        // we're in the header, so loop thru each header element
        final List<String> columnNamesList = new ArrayList<String>();
        while (true) {
            t = p.nextToken(); // get an element from the header array
            if (t == null)
                throw new InvalidJsonException("unexpected end of output");
            else if (t.equals(JsonToken.END_ARRAY))
                break;
            else if (!t.equals(JsonToken.VALUE_STRING))
                throw new InvalidJsonException("header array can only contain strings");
            columnNamesList.add(p.getText());
        }
        if (columnNamesList.size() == 0) // empty header, so just exit
            return;

        final String[] cols = columnNamesList.toArray(new String[columnNamesList.size()]);
        delegate.header(cols);

        final Object[] elems = new Object[cols.length];
        final Class[]  types = new Class[cols.length];

        // loop thru each row
        while (true) {
            t = p.nextToken(); // get a row
            if (t == null)
                throw new InvalidJsonException("unexpected end of output");
            else if (t.equals(JsonToken.END_ARRAY))
                break;
            else if (!t.equals(JsonToken.START_ARRAY))
                throw new InvalidJsonException("rows can only be arrays");

            // loop thru each element in row
            int elemIndex = 0;
            while (true) {
                t = p.nextToken(); // get an element in the row
                if (t == null)
                    throw new InvalidJsonException("unexpected end of output");
                else if (t.equals(JsonToken.END_ARRAY))
                    break;

                if (elemIndex >= elems.length)
                    throw new InvalidJsonException("row has more than %d elements", elems.length);

                extractElem(p, t, elems, types, elemIndex);

                elemIndex++;
            }
            if (elemIndex < elems.length)
                throw new InvalidJsonException("row has %d elements but header has %d elements", elemIndex, elems.length);

            delegate.row(elems, types);
        }

        delegate.done();
    }

    private static void extractElem(final JsonParser p, final JsonToken t, final Object[] elems, final Class[] types, final int elemIndex) throws IOException, InvalidJsonException {
        Object elem;
        Class type;
        if (t.equals(JsonToken.VALUE_NULL)) {
            elem = null;
            type = null;
        } else if (t.equals(JsonToken.VALUE_TRUE)) {
            elem = Boolean.TRUE;
            type = Boolean.class;
        } else if (t.equals(JsonToken.VALUE_FALSE)) {
            elem = Boolean.FALSE;
            type = Boolean.class;
        } else if (t.equals(JsonToken.VALUE_STRING)) {
            elem = p.getText();
            type = String.class;
        } else if (t.equals(JsonToken.VALUE_NUMBER_INT)) {
            elem = new Long(p.getLongValue());
            type = Long.class;
        } else if (t.equals(JsonToken.VALUE_NUMBER_FLOAT)) {
            elem = new Double(p.getDoubleValue());
            type = Double.class;
        } else {
            throw new InvalidJsonException("row elements can only be these primitives: null, booleans, strings, and numbers");
        }

        final Class expectedType = types[elemIndex];
        if (elem != null) {
            if (expectedType == null) {
                types[elemIndex] = type;
            } else if (!expectedType.equals(type)) {
                throw new InvalidJsonException("row element has type '%s' but is expected to be '%s'", type, expectedType);
            }
        }

        elems[elemIndex] = elem;
    }
}

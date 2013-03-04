package EvolvoApp.internal.json;

import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;

public class JsonTableReaderTest
{
    @Test
    public void testEmptyArray() throws Exception {
        JsonTableReader.read(str("[]"), new DumbDelegate());
    }

    @Test(expected = InvalidJsonException.class)
    public void testEmptyObj() throws Exception {
        JsonTableReader.read(str("{}"), new DumbDelegate());
    }

    @Test(expected = InvalidJsonException.class)
    public void headerNotArray() throws Exception {
        JsonTableReader.read(str("[0]"), new DumbDelegate());
    }

    @Test
    public void headerEmpty() throws Exception {
        JsonTableReader.read(str("[[]]"), new DumbDelegate());
    }

    @Test(expected = InvalidJsonException.class)
    public void headerNotAllStrings() throws Exception {
        JsonTableReader.read(str("[[\"a\", \"b\", 0]]"), new DumbDelegate());
    }

    @Test(expected = InvalidJsonException.class)
    public void headerHasNull() throws Exception {
        JsonTableReader.read(str("[[\"a\", null, \"b\"]]"), new DumbDelegate());
    }

    @Test
    public void testHeader() throws Exception {
        JsonTableReader.read(str("[[\"a\", \"b\"]]"), new DumbDelegate() {
            public void header(String[] cols) {
                String[] expectedCols = {"a", "b"};
                assertArrayEquals(cols, expectedCols);
            }
        });
    }

    @Test(expected = InvalidJsonException.class)
    public void testRowNotArray() throws Exception {
        JsonTableReader.read(str("[[\"a\", \"b\"], 0, 1]"), new DumbDelegate());
    }

    @Test(expected = InvalidJsonException.class)
    public void testRowMoreElems() throws Exception {
        JsonTableReader.read(str("[[\"a\", \"b\", \"c\"], [0, 1, 2, 3]]"), new DumbDelegate());
    }

    @Test(expected = InvalidJsonException.class)
    public void testRowFewElems() throws Exception {
        JsonTableReader.read(str("[[\"a\", \"b\", \"c\"], [0, 1]]"), new DumbDelegate());
    }

    @Test(expected = InvalidJsonException.class)
    public void testInvalidCellType() throws Exception {
        JsonTableReader.read(str("[[\"a\", \"b\", \"c\"], [0, 1, {\"x\": 2}]]"), new DumbDelegate());
    }

    @Test
    public void testEverything() throws Exception {
        System.out.println();
        System.out.println();
        JsonTableReader.read(str(
                    "[\n" +
                    " [\"a\", \"b\", \"c\", \"d\"],\n" +
                    " [0    , true , \"w\", 0.1  ],\n" +
                    " [1    , false, \"x\", 0.2  ],\n" +
                    " [2    , false, \"y\", 0.3  ],\n" +
                    " [3    , true , null , 0.4  ]\n" +
                    "]"),
                (new SmartDelegate())
                    .cols("a", "b", "c", "d")
                    .types(Long.class, Boolean.class, String.class, Double.class)
                    .row(0L, true, "w", 0.1)
                    .row(1L, false, "x", 0.2)
                    .row(2L, false, "y", 0.3)
                    .row(3L, true, null, 0.4));
                    
    }


    private static JsonParser str(final String input) throws Exception {
        return (new JsonFactory()).createJsonParser(input);
    }

    private static class DumbDelegate implements JsonTableReader.Delegate {
        public void header(String[] cols) {}
        public void row(Object[] elems, Class[] types) {}
        public void done() {}
    }

    private static class SmartDelegate implements JsonTableReader.Delegate {
        String[]        givenCols;
        Class[]         givenTypes;
        List<Object[]>  givenRows = new ArrayList<Object[]>();
        int rowIndex = 0;

        public SmartDelegate cols(String... cols) {
            givenCols = cols;
            return this;
        }

        public SmartDelegate types(Class... types) {
            givenTypes = types;
            assertEquals(givenTypes.length, givenCols.length);
            return this;
        }

        public SmartDelegate row(Object... elems) {
            givenRows.add(elems);
            assertEquals(elems.length, givenCols.length);
            return this;
        }

        public void header(String[] cols) {
            assertArrayEquals(cols, givenCols);
        }

        public void row(Object[] elems, Class[] types) {
            assertEquals(types.length, givenTypes.length);
            for (int i = 0; i < types.length; i++) {
                if (types[i] == null)
                    continue;
                assertEquals(types[i], givenTypes[i]);
            }
            final Object[] givenElems = givenRows.get(rowIndex);
            assertArrayEquals(givenElems, elems);
            rowIndex++;
        }

        public void done() {
            assertEquals(rowIndex, givenRows.size());
        }
    }
}

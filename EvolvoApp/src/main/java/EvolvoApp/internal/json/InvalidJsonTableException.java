package EvolvoApp.internal.json;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonLocation;

class InvalidJsonTableException extends Exception {
    public InvalidJsonTableException(String fmt, Object... args) {
        super(String.format(fmt, args));
    }
    public InvalidJsonTableException(Throwable cause, String fmt, Object... args) {
        super(String.format(fmt, args), cause);
    }
}

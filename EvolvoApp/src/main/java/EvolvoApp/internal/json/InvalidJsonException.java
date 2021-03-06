package EvolvoApp.internal.json;

public class InvalidJsonException extends Exception {
    public InvalidJsonException(String fmt, Object... args) {
        super(String.format(fmt, args));
    }
    public InvalidJsonException(Throwable cause, String fmt, Object... args) {
        super(String.format(fmt, args), cause);
    }
}

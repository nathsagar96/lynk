package ly.lynk.exception;

/** Thrown when a URL violates business rules, such as referencing its own short URL. */
public class InvalidUrlException extends RuntimeException {

    /**
     * Creates an exception with the error message.
     *
     * @param message the detail message
     */
    public InvalidUrlException(String message) {
        super(message);
    }
}

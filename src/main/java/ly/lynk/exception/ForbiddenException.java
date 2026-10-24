package ly.lynk.exception;

/**
 * Thrown when an authenticated user attempts to access or modify a resource they do not own.
 */
public class ForbiddenException extends RuntimeException {

    /**
     * Constructs a new forbidden exception with the given message.
     *
     * @param message the detail message
     */
    public ForbiddenException(String message) {
        super(message);
    }
}

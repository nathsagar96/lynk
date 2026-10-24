package ly.lynk.exception;

/** Thrown when a unique alias cannot be generated within the configured retry limit. */
public class AliasGenerationException extends RuntimeException {

    /**
     * Creates an exception with the error message.
     *
     * @param message the detail message
     */
    public AliasGenerationException(String message) {
        super(message);
    }
}

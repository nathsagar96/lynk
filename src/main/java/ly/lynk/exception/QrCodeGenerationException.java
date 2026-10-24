package ly.lynk.exception;

/** Exception thrown when generating a QR code image fails unexpectedly. */
public class QrCodeGenerationException extends RuntimeException {

    /**
     * Creates a new QR code generation exception with the specified message and cause.
     *
     * @param message descriptive failure message
     * @param cause underlying cause
     */
    public QrCodeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

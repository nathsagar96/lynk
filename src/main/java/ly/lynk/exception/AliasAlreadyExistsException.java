package ly.lynk.exception;

/** Thrown when a requested custom alias is already in use. */
public class AliasAlreadyExistsException extends RuntimeException {

    /**
     * Creates an exception for the conflicting alias.
     *
     * @param alias the conflicting alias
     */
    public AliasAlreadyExistsException(String alias) {
        super("Alias already exists: " + alias);
    }
}

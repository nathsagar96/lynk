package ly.lynk.exception;

/** Thrown when a requested alias does not exist. */
public class AliasNotFoundException extends RuntimeException {

    /**
     * Creates an exception for the missing alias.
     *
     * @param alias the missing alias
     */
    public AliasNotFoundException(String alias) {
        super("Alias not found: " + alias);
    }
}

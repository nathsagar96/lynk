package ly.lynk.exception;

/** Thrown when an alias is accessed after its expiration timestamp. */
public class AliasExpiredException extends RuntimeException {

    /**
     * Creates an exception for the expired alias.
     *
     * @param alias the expired alias
     */
    public AliasExpiredException(String alias) {
        super("Alias has expired: " + alias);
    }
}

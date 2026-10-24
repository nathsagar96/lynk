package ly.lynk.exception;

/** Thrown when a custom alias matches a reserved system keyword. */
public class ReservedAliasException extends RuntimeException {

    /**
     * Creates an exception for the reserved alias.
     *
     * @param alias the reserved alias
     */
    public ReservedAliasException(String alias) {
        super("Alias is reserved and cannot be used: " + alias);
    }
}

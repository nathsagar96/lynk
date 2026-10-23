package ly.lynk.exception;

public class ReservedAliasException extends RuntimeException {

    public ReservedAliasException(String alias) {
        super("Alias is reserved and cannot be used: " + alias);
    }
}

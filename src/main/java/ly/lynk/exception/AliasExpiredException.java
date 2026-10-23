package ly.lynk.exception;

public class AliasExpiredException extends RuntimeException {

    public AliasExpiredException(String alias) {
        super("Alias has expired: " + alias);
    }
}

package ly.lynk.exception;

import org.springframework.http.HttpStatus;

public final class AliasAlreadyExistsException extends ApplicationException {

    public AliasAlreadyExistsException(String alias) {
        super("Alias '%s' is already taken".formatted(alias), HttpStatus.CONFLICT);
    }
}

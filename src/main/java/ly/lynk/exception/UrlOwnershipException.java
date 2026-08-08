package ly.lynk.exception;

import org.springframework.http.HttpStatus;

public final class UrlOwnershipException extends ApplicationException {

    public UrlOwnershipException() {
        super("URL not found or not owned by you", HttpStatus.FORBIDDEN);
    }
}

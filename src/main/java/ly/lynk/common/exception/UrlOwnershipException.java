package ly.lynk.common.exception;

import org.springframework.http.HttpStatus;

public final class UrlOwnershipException extends ApplicationException {

    public UrlOwnershipException(String shortcode, String userId) {
        super("User '%s' does not own URL: %s".formatted(userId, shortcode), HttpStatus.FORBIDDEN);
    }
}

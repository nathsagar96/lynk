package ly.lynk.exception;

import org.springframework.http.HttpStatus;

public final class UrlExpiredException extends ApplicationException {

    public UrlExpiredException(String shortcode) {
        super("URL has expired: %s".formatted(shortcode), HttpStatus.GONE);
    }
}

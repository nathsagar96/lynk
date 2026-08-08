package ly.lynk.exception;

import org.springframework.http.HttpStatus;

public final class UrlNotFoundException extends ApplicationException {

    public UrlNotFoundException(String shortcode) {
        super("URL not found: %s".formatted(shortcode), HttpStatus.NOT_FOUND);
    }
}

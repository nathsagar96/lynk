package ly.lynk.common.exception;

import org.springframework.http.HttpStatus;

public abstract sealed class ApplicationException extends RuntimeException
        permits UrlNotFoundException, UrlExpiredException, AliasAlreadyExistsException, UrlOwnershipException {

    private final HttpStatus httpStatus;

    protected ApplicationException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}

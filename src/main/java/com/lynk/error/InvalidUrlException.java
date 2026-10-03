package com.lynk.error;

/**
 * The submitted URL or alias is absent, malformed, not absolute, or uses an unsupported scheme.
 */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String detail) {
        super(detail);
    }
}

package com.lynk.error;

/**
 * No mapping exists for the requested short code.
 */
public class UrlNotFoundException extends RuntimeException {

    public UrlNotFoundException(String shortCode) {
        super("No shortened URL exists for code '" + shortCode + "'.");
    }
}

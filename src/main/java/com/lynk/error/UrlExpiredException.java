package com.lynk.error;

import java.time.Instant;

/**
 * The short code exists but its expiry moment has passed.
 */
public class UrlExpiredException extends RuntimeException {

    public UrlExpiredException(String shortCode, Instant expiresAt) {
        super("The shortened URL for code '" + shortCode + "' expired at " + expiresAt + ".");
    }
}

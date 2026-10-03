package com.lynk.error;

import java.time.Instant;
import java.util.Collection;
import org.springframework.http.HttpStatus;

/** Single application exception; carries its own RFC 9457 status, type and title. */
public class LinkException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String title;

    private LinkException(HttpStatus status, String title, String type, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
        this.type = type;
    }

    public HttpStatus status() {
        return status;
    }

    public String type() {
        return type;
    }

    public String title() {
        return title;
    }

    public static LinkException notFound(String shortCode) {
        return new LinkException(
                HttpStatus.NOT_FOUND,
                "Short URL not found",
                "/problems/url-not-found",
                "No shortened URL exists for code '" + shortCode + "'.");
    }

    public static LinkException expired(String shortCode, Instant expiresAt) {
        return new LinkException(
                HttpStatus.GONE,
                "Short URL expired",
                "/problems/url-expired",
                "The shortened URL for code '" + shortCode + "' expired at " + expiresAt + ".");
    }

    public static LinkException aliasConflict(String alias) {
        return new LinkException(
                HttpStatus.CONFLICT,
                "Alias already exists",
                "/problems/alias-conflict",
                "The custom alias '" + alias + "' is already in use.");
    }

    public static LinkException reserved(String alias, Collection<String> reserved) {
        return new LinkException(
                HttpStatus.BAD_REQUEST,
                "Reserved alias",
                "/problems/reserved-alias",
                "The alias '" + alias + "' is reserved. Reserved aliases: " + String.join(", ", reserved) + ".");
    }

    public static LinkException invalid(String detail) {
        return new LinkException(HttpStatus.BAD_REQUEST, "Invalid request", "/problems/invalid-url", detail);
    }
}

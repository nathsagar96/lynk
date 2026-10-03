package com.lynk.service;

import com.lynk.config.UrlShortenerProperties;
import com.lynk.error.LinkException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates and normalises caller input before it reaches the database.
 */
@Component
@RequiredArgsConstructor
public class UrlValidator {

    /**
     * Shared alphabet for generated codes and hand-picked aliases, so the redirect mapping's
     * pattern accepts both without a second code path.
     */
    public static final String CODE_REGEX = "[A-Za-z0-9_-]{3,32}";

    private static final Pattern CODE_PATTERN = Pattern.compile("^" + CODE_REGEX + "$");

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final UrlShortenerProperties properties;

    /**
     * Checks the long URL is an absolute http(s) URL and returns it normalised.
     * <p>
     * Normalising up front is what guarantees {@code URI.create(url)} on the redirect path cannot
     * throw after the mapping has already been committed.
     */
    public String validateUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw LinkException.invalid("A URL is required.");
        }
        String trimmed = rawUrl.trim();
        int maxLength = properties.code().maxUrlLength();
        if (trimmed.length() > maxLength) {
            throw LinkException.invalid("URL must be at most " + maxLength + " characters.");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException ex) {
            throw LinkException.invalid("URL is not a valid URI: " + ex.getReason() + ".");
        }

        if (!uri.isAbsolute() || uri.getScheme() == null) {
            throw LinkException.invalid("URL must be absolute, including the http or https scheme.");
        }
        String scheme = uri.getScheme().toLowerCase();
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw LinkException.invalid("Only http and https URLs are supported.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw LinkException.invalid("URL must include a host.");
        }

        // Rebuild from the lowercased scheme so the stored value is canonical.
        return scheme + trimmed.substring(scheme.length());
    }

    /**
     * Checks a caller-chosen alias is well-formed and not reserved.
     */
    public String validateAlias(String rawAlias) {
        String alias = rawAlias.trim();
        if (!CODE_PATTERN.matcher(alias).matches()) {
            throw LinkException.invalid(
                    "Custom alias must be 3-32 characters of letters, digits, hyphen or underscore.");
        }
        if (properties.reservedWords().contains(alias.toLowerCase())) {
            throw LinkException.reserved(alias, properties.reservedWords());
        }
        return alias;
    }
}

package com.lynk.service;

import com.lynk.config.UrlShortenerProperties;
import com.lynk.error.InvalidUrlException;
import com.lynk.error.ReservedAliasException;
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
     * {@code [A-Za-z0-9_-]} pattern accepts both without a second code path.
     */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final UrlShortenerProperties properties;

    /**
     * Checks the long URL is an absolute http(s) URL and returns it normalised.
     * <p>
     * Normalising up front is what guarantees {@code URI.create(url)} on the redirect path cannot
     * throw after the mapping has already been committed.
     *
     * @throws InvalidUrlException if the URL is blank, too long, relative, or not http(s)
     */
    public String validateUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException("A URL is required.");
        }
        String trimmed = rawUrl.trim();
        int maxLength = properties.code().maxUrlLength();
        if (trimmed.length() > maxLength) {
            throw new InvalidUrlException("URL must be at most " + maxLength + " characters.");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException ex) {
            throw new InvalidUrlException("URL is not a valid URI: " + ex.getReason() + ".");
        }

        if (!uri.isAbsolute() || uri.getScheme() == null) {
            throw new InvalidUrlException("URL must be absolute, including the http or https scheme.");
        }
        String scheme = uri.getScheme().toLowerCase();
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new InvalidUrlException("Only http and https URLs are supported.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("URL must include a host.");
        }

        // Rebuild from the lowercased scheme so the stored value is canonical.
        return scheme + trimmed.substring(scheme.length());
    }

    /**
     * Checks a caller-chosen alias is well-formed and not reserved.
     *
     * @throws InvalidUrlException    if the alias does not match the shared code pattern
     * @throws ReservedAliasException if the alias would shadow an existing route
     */
    public String validateAlias(String rawAlias) {
        String alias = rawAlias.trim();
        if (!CODE_PATTERN.matcher(alias).matches()) {
            throw new InvalidUrlException(
                    "Custom alias must be 3-32 characters of letters, digits, hyphen or underscore.");
        }
        if (properties.reservedWords().contains(alias.toLowerCase())) {
            throw new ReservedAliasException(alias, properties.reservedWords());
        }
        return alias;
    }
}

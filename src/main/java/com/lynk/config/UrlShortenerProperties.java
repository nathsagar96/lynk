package com.lynk.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable behaviour of the shortener, bound from the {@code lynk.*} configuration namespace.
 *
 * @param code          short code generation settings
 * @param cleanup       scheduled expiry sweep settings
 * @param baseUrl       absolute base used to build the {@code shortUrl} handed back to callers
 * @param reservedWords short codes and aliases that may never be registered because they would
 *                      shadow application or framework routes
 */
@ConfigurationProperties(prefix = "lynk")
public record UrlShortenerProperties(Code code, Cleanup cleanup, String baseUrl, Set<String> reservedWords) {

    /**
     * Short code generation settings.
     *
     * @param length       number of base-62 characters in a generated code
     * @param maxAttempts  how many times a colliding code is regenerated before giving up
     * @param maxUrlLength upper bound on the accepted long URL
     */
    public record Code(int length, int maxAttempts, int maxUrlLength) {}

    /**
     * Scheduled expiry sweep settings.
     *
     * @param cron cron expression driving the cleanup job
     */
    public record Cleanup(String cron) {}
}

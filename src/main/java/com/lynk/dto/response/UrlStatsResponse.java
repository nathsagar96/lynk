package com.lynk.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Response body for {@code GET /api/v1/url/stats/{shortCode}}.
 *
 * @param originalUrl the destination
 * @param shortUrl    absolute shortened URL
 * @param createdAt   creation moment
 * @param expiresAt   expiry moment, or null if the link never expires
 * @param clickCount  number of recorded redirects
 */
public record UrlStatsResponse(
        @Schema(
                description = "The destination as it was stored, with a lower-cased scheme.",
                example = "https://spring.io/guides")
        String originalUrl,

        @Schema(
                description = "The shortened URL, rebuilt from the configured base URL.",
                example = "http://localhost:8080/readme-demo")
        String shortUrl,

        @Schema(description = "When the link was registered.", example = "2026-09-28T05:09:39.484023Z")
        Instant createdAt,

        @Schema(
                description = "When the link stopped redirecting. Null when it never expires.",
                example = "2026-09-29T05:09:39.484046Z",
                nullable = true)
        Instant expiresAt,

        @Schema(
                description =
                        "How many times the link has been followed. Readable after expiry, until the nightly sweep deletes it.",
                example = "12")
        long clickCount) {}

package com.lynk.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Response body for a successful {@code POST /api/v1/url/shorten}.
 *
 * @param shortUrl    absolute shortened URL, ready to share
 * @param shortCode   the code segment on its own
 * @param originalUrl the normalised destination
 * @param expiresAt   expiry moment, or null if the link never expires
 */
public record ShortenUrlResponse(
        @Schema(description = "The shortened URL, ready to share.", example = "http://localhost:8080/readme-demo")
        String shortUrl,

        @Schema(
                description = "The short code on its own, which is the last path segment of `shortUrl`.",
                example = "readme-demo")
        String shortCode,

        @Schema(
                description = "The destination as it was stored, with a lower-cased scheme.",
                example = "https://spring.io/guides")
        String originalUrl,

        @Schema(
                description = "When the link stops redirecting. Null when it never expires.",
                example = "2026-09-29T05:09:39.484046Z",
                nullable = true)
        Instant expiresAt) {}

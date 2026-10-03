package com.lynk.dto.response;

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
        String originalUrl, String shortUrl, Instant createdAt, Instant expiresAt, long clickCount) {}

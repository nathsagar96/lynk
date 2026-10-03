package com.lynk.dto.response;

import java.time.Instant;

/**
 * Response body for a successful {@code POST /api/v1/url/shorten}.
 *
 * @param shortUrl    absolute shortened URL, ready to share
 * @param shortCode   the code segment on its own
 * @param originalUrl the normalised destination
 * @param expiresAt   expiry moment, or null if the link never expires
 */
public record ShortenUrlResponse(String shortUrl, String shortCode, String originalUrl, Instant expiresAt) {}

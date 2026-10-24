package ly.lynk.dto;

import java.time.Instant;

/**
 * Response payload containing shortened URL details.
 *
 * @param alias the unique shortened alias
 * @param originalUrl the target URL
 * @param shortUrl the fully-qualified public short URL
 * @param createdAt timestamp when the alias was created
 * @param expiresAt optional expiration timestamp, or {@code null} if it does not expire
 */
public record ShortenUrlResponse(
        String alias, String originalUrl, String shortUrl, Instant createdAt, Instant expiresAt) {}

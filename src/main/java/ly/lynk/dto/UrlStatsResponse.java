package ly.lynk.dto;

import java.time.Instant;

/**
 * Response payload containing click statistics and metadata for a shortened URL.
 *
 * @param alias the unique shortened alias
 * @param originalUrl the target URL
 * @param shortUrl the fully-qualified public short URL
 * @param clickCount the total recorded click count
 * @param createdAt timestamp when the alias was created
 * @param expiresAt optional expiration timestamp, or {@code null} if it does not expire
 */
public record UrlStatsResponse(
        String alias, String originalUrl, String shortUrl, long clickCount, Instant createdAt, Instant expiresAt) {}

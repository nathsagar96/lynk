package ly.lynk.dto;

import java.time.Instant;
import ly.lynk.model.UrlMapping;

/**
 * Immutable DTO representing a cached URL mapping for resolution and metadata inspection.
 *
 * @param alias the unique shortened alias
 * @param originalUrl the destination target URL
 * @param createdAt the creation instant
 * @param expiresAt the optional expiration instant, or {@code null}
 * @param ownerId the optional owner subject identifier, or {@code null}
 */
public record CachedUrlMapping(String alias, String originalUrl, Instant createdAt, Instant expiresAt, String ownerId) {

    /**
     * Converts a JPA {@link UrlMapping} entity to an immutable {@link CachedUrlMapping} record.
     *
     * @param mapping the source database mapping
     * @return the cached record
     */
    public static CachedUrlMapping from(UrlMapping mapping) {
        return new CachedUrlMapping(
                mapping.getAlias(),
                mapping.getOriginalUrl(),
                mapping.getCreatedAt(),
                mapping.getExpiresAt(),
                mapping.getOwnerId());
    }
}

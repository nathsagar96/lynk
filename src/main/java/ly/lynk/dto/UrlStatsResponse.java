package ly.lynk.dto;

import java.time.Instant;

public record UrlStatsResponse(
        String alias, String originalUrl, String shortUrl, long clickCount, Instant createdAt, Instant expiresAt) {}

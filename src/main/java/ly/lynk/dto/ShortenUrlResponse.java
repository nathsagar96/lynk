package ly.lynk.dto;

import java.time.Instant;

public record ShortenUrlResponse(
        String alias, String originalUrl, String shortUrl, Instant createdAt, Instant expiresAt) {}

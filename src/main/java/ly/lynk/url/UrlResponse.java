package ly.lynk.url;

import java.time.Instant;

public record UrlResponse(String shortcode, String originalUrl, Instant expiresAt, Instant createdAt) {}

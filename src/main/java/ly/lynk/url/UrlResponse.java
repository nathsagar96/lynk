package ly.lynk.url;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Short URL details")
public record UrlResponse(
        @Schema(description = "Unique shortcode identifier", example = "s9Kx2mLp")
        String shortcode,

        @Schema(
                description = "Original destination URL",
                example = "https://docs.spring.io/spring-boot/reference/data/access.html#data.sql.jdbc-and-spring-data")
        String originalUrl,

        @Schema(
                description = "Expiration timestamp (ISO-8601, null if no expiry set)",
                example = "2026-12-31T23:59:59Z")
        Instant expiresAt,

        @Schema(description = "Creation timestamp (ISO-8601)", example = "2026-08-09T10:15:32Z")
        Instant createdAt) {}

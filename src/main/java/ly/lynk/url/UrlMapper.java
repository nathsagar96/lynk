package ly.lynk.url;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class UrlMapper {

    public UrlResponse toResponse(UrlEntity entity) {
        return new UrlResponse(
                entity.getShortcode(), entity.getOriginalUrl(), entity.getExpiresAt(), entity.getCreatedAt());
    }

    public UrlResponse toResponse(String shortcode, String originalUrl, Instant expiresAt, Instant createdAt) {
        return new UrlResponse(shortcode, originalUrl, expiresAt, createdAt);
    }
}

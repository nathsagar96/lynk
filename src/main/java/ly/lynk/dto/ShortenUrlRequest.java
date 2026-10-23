package ly.lynk.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import ly.lynk.validation.HttpUrl;

public record ShortenUrlRequest(
        @NotBlank(message = "originalUrl must not be blank")
        @Size(max = 2048, message = "originalUrl must not exceed 2048 characters")
        @HttpUrl
        String originalUrl,

        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$", message = "customAlias must match ^[a-zA-Z0-9_-]{3,32}$")
        String customAlias,

        @Future(message = "Expiration time must be in the future")
        Instant expiresAt) {

    public ShortenUrlRequest {
        originalUrl = originalUrl == null ? null : originalUrl.trim();
        customAlias = customAlias == null ? null : customAlias.trim();
    }
}

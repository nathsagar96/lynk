package com.lynk.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/url/shorten}.
 *
 * @param url           the destination to shorten; required
 * @param customAlias   optional caller-chosen code
 * @param hoursToExpire optional lifetime in hours; null means the link never expires
 */
public record ShortenUrlRequest(
        @NotBlank(message = "url must not be blank")
        @Schema(
                description = "Absolute http or https URL with a host, at most 2048 characters. The "
                        + "scheme is lower-cased and the URL stored normalised, so `HTTPS://…` and "
                        + "`https://…` resolve to the same destination.",
                example = "https://spring.io/guides")
        String url,

        @Schema(
                description = "A code chosen by the caller instead of a generated one. 3-32 characters of "
                        + "`A-Za-z0-9_-`, not a reserved word, and not already taken.",
                example = "readme-demo",
                pattern = "^[A-Za-z0-9_-]{3,32}$",
                minLength = 3,
                maxLength = 32)
        String customAlias,

        @Min(value = 1, message = "hoursToExpire must be at least 1")
        @Max(value = 8760, message = "hoursToExpire must be at most 8760 (one year)")
        @Schema(description = "Lifetime in hours. Omit it and the link never expires.", example = "24")
        Integer hoursToExpire) {}

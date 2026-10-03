package com.lynk.dto.request;

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
        @NotBlank(message = "url must not be blank") String url,
        String customAlias,

        @Min(value = 1, message = "hoursToExpire must be at least 1")
        @Max(value = 8760, message = "hoursToExpire must be at most 8760 (one year)")
        Integer hoursToExpire) {}

package com.lynk.controller;

import com.lynk.domain.UrlMapping;
import com.lynk.dto.request.ShortenUrlRequest;
import com.lynk.dto.response.ShortenUrlResponse;
import com.lynk.dto.response.UrlStatsResponse;
import com.lynk.service.UrlShortenerService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shorten and inspect API, versioned under {@code /api/v1/url}.
 * <p>
 * Returns and accepts DTOs only; the {@link UrlMapping} entity never leaves the
 * service layer.
 */
@RestController
@RequestMapping("/api/v1/url")
@RequiredArgsConstructor
public class UrlController {

    private final UrlShortenerService urlShortenerService;

    /**
     * Registers a long URL and returns its shortened form.
     *
     * @param request validated against the constraints on {@link ShortenUrlRequest}; an invalid
     *                URL or alias is reported as a problem document
     * @return 201 with the new short URL, its code, the normalised destination and any expiry, and
     * a {@code Location} header naming the newly created short link
     */
    @PostMapping("/shorten")
    public ResponseEntity<ShortenUrlResponse> shorten(@Valid @RequestBody ShortenUrlRequest request) {
        ShortenUrlResponse response = urlShortenerService.shorten(request);
        return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
    }

    /**
     * Returns click statistics for a short code, including for links that have expired.
     *
     * @param shortCode the code to look up, constrained to the shared code alphabet
     * @return 200 with the stored mapping and its click count
     */
    @GetMapping("/stats/{shortCode:[A-Za-z0-9_-]{3,32}}")
    public ResponseEntity<UrlStatsResponse> stats(@PathVariable String shortCode) {
        return ResponseEntity.ok(urlShortenerService.stats(shortCode));
    }
}

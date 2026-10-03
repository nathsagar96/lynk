package com.lynk.controller;

import com.lynk.service.UrlShortenerService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resolves short codes to their destination.
 */
@RestController
@RequiredArgsConstructor
public class RedirectController {

    /**
     * Constrained so that single-segment paths which are not codes, such as {@code /favicon.ico},
     * fall through to normal 404 handling instead of being reported as missing short URLs. The
     * pattern matches the alphabet and bounds used for alias validation, so aliases and generated
     * codes are indistinguishable here.
     */
    private static final String CODE_PATH = "/{shortCode:[A-Za-z0-9_-]{3,32}}";

    private final UrlShortenerService urlShortenerService;

    @GetMapping(CODE_PATH)
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = urlShortenerService.resolveAndCountClick(shortCode);
        // 302 rather than 301: the destination and the click count can both change, so the
        // redirect must not be cached by the client as permanent.
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}

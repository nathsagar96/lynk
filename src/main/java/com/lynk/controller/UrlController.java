package com.lynk.controller;

import com.lynk.domain.UrlMapping;
import com.lynk.dto.request.ShortenUrlRequest;
import com.lynk.dto.response.ShortenUrlResponse;
import com.lynk.dto.response.UrlStatsResponse;
import com.lynk.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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
 * Returns and accepts DTOs only; the {@link UrlMapping} entity never leaves the service layer.
 */
@Tag(name = "Short URLs", description = "Create shortened links and read their click counts")
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
    @Operation(
            summary = "Shorten a URL",
            description = "Registers a long URL and returns its shortened form. Supply `customAlias` to "
                    + "pick the code yourself, or omit it and let one be generated. `hoursToExpire` bounds "
                    + "the link's life; omit it for a link that never expires.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "The link was created. `Location` holds the same value as `shortUrl`.",
                headers = @Header(name = "Location", description = "The newly created short URL")),
        @ApiResponse(
                responseCode = "400",
                description = "The request was rejected. `type` is one of `/problems/validation-failed` "
                        + "(bean validation failed, with a per-field `errors` array), `/problems/invalid-url` "
                        + "(the URL or alias is unusable) or `/problems/reserved-alias` (the alias would shadow "
                        + "an application route).",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "`type` is `/problems/alias-conflict`: the custom alias is already taken.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class)))
    })
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
    @Operation(
            summary = "Read the stats of a short code",
            description = "Returns the stored mapping and how many times it has been followed. Works for "
                    + "expired links too, and until the nightly sweep deletes them.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The stored mapping and its click count."),
        @ApiResponse(
                responseCode = "404",
                description = "`type` is `/problems/url-not-found`: no such short code exists.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/stats/{shortCode:[A-Za-z0-9_-]{3,32}}")
    public ResponseEntity<UrlStatsResponse> stats(
            @Parameter(
                            description = "The short code to look up, as used in the path of the short link",
                            example = "readme-demo")
                    @PathVariable
                    String shortCode) {
        return ResponseEntity.ok(urlShortenerService.stats(shortCode));
    }
}

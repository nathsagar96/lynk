package com.lynk.controller;

import com.lynk.domain.UrlMapping;
import com.lynk.dto.request.ShortenUrlRequest;
import com.lynk.dto.response.LinkPageResponse;
import com.lynk.dto.response.ShortenUrlResponse;
import com.lynk.dto.response.UrlStatsResponse;
import com.lynk.error.LinkException;
import com.lynk.service.UrlShortenerService;
import com.lynk.service.UrlValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shorten and inspect API, versioned under {@code /api/v1/url}.
 * <p>
 * Returns and accepts DTOs only; the {@link UrlMapping} entity never leaves the service layer.
 * <p>
 * Both operations read the caller's identity from the bearer token, which the security filter chain
 * has already validated. The token's subject is what a link is owned by, and stats are scoped to it.
 */
@Tag(name = "Short URLs", description = "Create, list and delete shortened links, and read their click counts")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/url")
@RequiredArgsConstructor
public class UrlController {

    private static final int MAX_PAGE_SIZE = 100;

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
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "`type` is `/problems/unauthenticated`: no bearer token was sent, or the "
                        + "token was invalid or expired.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/shorten")
    public ResponseEntity<ShortenUrlResponse> shorten(
            @Valid @RequestBody ShortenUrlRequest request, @AuthenticationPrincipal Jwt jwt) {
        ShortenUrlResponse response = urlShortenerService.shorten(request, ownerOf(jwt));
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
                    + "expired links too, and until the nightly sweep deletes them. Only the subject that "
                    + "registered the link can read it; every other caller gets the same 404 as an "
                    + "unknown code, so stats never confirm that somebody else's link exists.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The stored mapping and its click count."),
        @ApiResponse(
                responseCode = "401",
                description = "`type` is `/problems/unauthenticated`: no bearer token was sent, or the "
                        + "token was invalid or expired.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "`type` is `/problems/url-not-found`: no such short code exists, or it "
                        + "belongs to another user. The two are deliberately indistinguishable.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/stats/{shortCode:" + UrlValidator.CODE_REGEX + "}")
    public ResponseEntity<UrlStatsResponse> stats(
            @Parameter(
                            description = "The short code to look up, as used in the path of the short link",
                            example = "readme-demo")
                    @PathVariable
                    String shortCode,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(urlShortenerService.stats(shortCode, ownerOf(jwt)));
    }

    /**
     * Lists the links the caller registered, newest first.
     *
     * @param page zero-based page index
     * @param size links per page, at most {@code MAX_PAGE_SIZE}
     * @return 200 with one page of links and the totals needed to walk the rest
     */
    @Operation(
            summary = "List your links",
            description = "Returns one page of the links you registered, newest first. Expired links are "
                    + "included until the nightly sweep deletes them. Walk them with `page` and `size`; "
                    + "`totalElements` says how many there are to walk. Links owned by other users are "
                    + "never included.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "One page of your links. Empty when you have none."),
        @ApiResponse(
                responseCode = "400",
                description = "`type` is `/problems/validation-failed`: `page` was negative, or `size` was "
                        + "outside 1-" + MAX_PAGE_SIZE + ".",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "`type` is `/problems/unauthenticated`: no bearer token was sent, or the "
                        + "token was invalid or expired.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/links")
    public ResponseEntity<LinkPageResponse> links(
            @Parameter(description = "Zero-based page index.", example = "0") @RequestParam(defaultValue = "0") @Min(0)
                    int page,
            @Parameter(description = "How many links to return, from 1 to " + MAX_PAGE_SIZE + ".", example = "20")
                    @RequestParam(defaultValue = "20")
                    @Min(1)
                    @Max(MAX_PAGE_SIZE)
                    int size,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(urlShortenerService.list(ownerOf(jwt), page, size));
    }

    /**
     * Deletes one of the caller's links, so its short code stops resolving.
     *
     * @param shortCode the code to delete, constrained to the shared code alphabet
     * @return 204 with no body
     */
    @Operation(
            summary = "Delete one of your links",
            description = "Removes the link. Its short code stops resolving immediately — following it "
                    + "becomes a 404 — and its click count goes with it.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "The link was deleted. No body."),
        @ApiResponse(
                responseCode = "401",
                description = "`type` is `/problems/unauthenticated`: no bearer token was sent, or the "
                        + "token was invalid or expired.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "`type` is `/problems/url-not-found`: no such short code exists, or it "
                        + "belongs to another user. The two are deliberately indistinguishable.")
    })
    @DeleteMapping("/links/{shortCode:" + UrlValidator.CODE_REGEX + "}")
    public ResponseEntity<Void> delete(
            @Parameter(
                            description = "The short code to delete, as used in the path of the short link",
                            example = "readme-demo")
                    @PathVariable
                    String shortCode,
            @AuthenticationPrincipal Jwt jwt) {
        urlShortenerService.delete(shortCode, ownerOf(jwt));
        return ResponseEntity.noContent().build();
    }

    /**
     * The owner the caller is, which is the token's subject and what every link is attributed to.
     * <p>
     * {@code sub} is optional in the general JWT access-token profile, so a token can pass signature
     * and issuer validation and still carry none. Registering a link under such a token would create
     * one that nobody can read back, because stats are matched on the subject — so it is rejected
     * here, at the boundary where the untrusted claim enters, rather than left as a silent orphan.
     */
    private static String ownerOf(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw LinkException.tokenWithoutSubject();
        }
        return subject;
    }
}

package ly.lynk.controller;

import jakarta.validation.Valid;
import java.net.URI;
import ly.lynk.config.AppProperties;
import ly.lynk.dto.ShortenUrlRequest;
import ly.lynk.dto.ShortenUrlResponse;
import ly.lynk.dto.UrlStatsResponse;
import ly.lynk.exception.ForbiddenException;
import ly.lynk.service.UrlShortenerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API endpoints for creating, retrieving, and managing URL mappings. */
@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlShortenerService service;
    private final AppProperties properties;

    /**
     * Creates a URL controller with the given service and application properties.
     *
     * @param service the URL shortener service
     * @param properties the application properties
     */
    public UrlController(UrlShortenerService service, AppProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    /**
     * Shortens a URL using either an auto-generated or custom alias, attributed to the caller.
     *
     * @param request the shorten request payload
     * @param jwt the authenticated JWT principal
     * @return 201 Created response containing short URL details and Location header
     */
    @PostMapping
    public ResponseEntity<ShortenUrlResponse> shorten(
            @Valid @RequestBody ShortenUrlRequest request, @AuthenticationPrincipal Jwt jwt) {
        String ownerId = jwt != null ? jwt.getSubject() : null;
        if (ownerId == null || ownerId.isBlank()) {
            throw new ForbiddenException("Authentication token is missing a subject identifier");
        }
        ShortenUrlResponse response = service.shortenUrl(request, ownerId);
        URI location = URI.create(properties.baseUrl() + "/api/v1/urls/" + response.alias());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Retrieves a paginated list of URLs owned by the authenticated caller.
     *
     * @param jwt the authenticated JWT principal
     * @param pageable pagination parameters
     * @return 200 OK response with a page of URL details
     */
    @GetMapping
    public ResponseEntity<Page<ShortenUrlResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        String ownerId = jwt != null ? jwt.getSubject() : null;
        if (ownerId == null || ownerId.isBlank()) {
            throw new ForbiddenException("Authentication token is missing a subject identifier");
        }
        return ResponseEntity.ok(service.getUserUrls(ownerId, pageable));
    }

    /**
     * Retrieves the public metadata and target URL for a given alias.
     *
     * @param alias the alias to look up
     * @return 200 OK response with the URL details
     */
    @GetMapping("/{alias}")
    public ResponseEntity<ShortenUrlResponse> get(@PathVariable String alias) {
        return ResponseEntity.ok(service.getUrlDetails(alias));
    }

    /**
     * Retrieves click statistics and metadata for a given alias if owned by the caller.
     *
     * @param alias the alias to query
     * @param jwt the authenticated JWT principal
     * @return 200 OK response with the click count and metadata
     */
    @GetMapping("/{alias}/stats")
    public ResponseEntity<UrlStatsResponse> stats(@PathVariable String alias, @AuthenticationPrincipal Jwt jwt) {
        String currentUserId = jwt != null ? jwt.getSubject() : null;
        return ResponseEntity.ok(service.getStats(alias, currentUserId));
    }

    /**
     * Deletes the URL mapping associated with the given alias if owned by the caller.
     *
     * @param alias the alias to delete
     * @param jwt the authenticated JWT principal
     * @return 204 No Content response
     */
    @DeleteMapping("/{alias}")
    public ResponseEntity<Void> delete(@PathVariable String alias, @AuthenticationPrincipal Jwt jwt) {
        String currentUserId = jwt != null ? jwt.getSubject() : null;
        service.deleteUrl(alias, currentUserId);
        return ResponseEntity.noContent().build();
    }
}

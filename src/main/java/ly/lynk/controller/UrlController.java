package ly.lynk.controller;

import jakarta.validation.Valid;
import java.net.URI;
import ly.lynk.config.AppProperties;
import ly.lynk.dto.ShortenUrlRequest;
import ly.lynk.dto.ShortenUrlResponse;
import ly.lynk.dto.UrlStatsResponse;
import ly.lynk.service.UrlShortenerService;
import org.springframework.http.ResponseEntity;
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
     * Shortens a URL using either an auto-generated or custom alias.
     *
     * @param request the shorten request payload
     * @return 201 Created response containing short URL details and Location header
     */
    @PostMapping
    public ResponseEntity<ShortenUrlResponse> shorten(@Valid @RequestBody ShortenUrlRequest request) {
        ShortenUrlResponse response = service.shortenUrl(request);
        URI location = URI.create(properties.baseUrl() + "/api/v1/urls/" + response.alias());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Retrieves the metadata and target URL for a given alias.
     *
     * @param alias the alias to look up
     * @return 200 OK response with the URL details
     */
    @GetMapping("/{alias}")
    public ResponseEntity<ShortenUrlResponse> get(@PathVariable String alias) {
        return ResponseEntity.ok(service.getUrlDetails(alias));
    }

    /**
     * Retrieves click statistics and metadata for a given alias.
     *
     * @param alias the alias to query
     * @return 200 OK response with the click count and metadata
     */
    @GetMapping("/{alias}/stats")
    public ResponseEntity<UrlStatsResponse> stats(@PathVariable String alias) {
        return ResponseEntity.ok(service.getStats(alias));
    }

    /**
     * Deletes the URL mapping associated with the given alias.
     *
     * @param alias the alias to delete
     * @return 204 No Content response
     */
    @DeleteMapping("/{alias}")
    public ResponseEntity<Void> delete(@PathVariable String alias) {
        service.deleteUrl(alias);
        return ResponseEntity.noContent().build();
    }
}

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

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlShortenerService service;
    private final AppProperties properties;

    public UrlController(UrlShortenerService service, AppProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<ShortenUrlResponse> shorten(@Valid @RequestBody ShortenUrlRequest request) {
        ShortenUrlResponse response = service.shortenUrl(request);
        URI location = URI.create(properties.baseUrl() + "/api/v1/urls/" + response.alias());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{alias}")
    public ResponseEntity<ShortenUrlResponse> get(@PathVariable String alias) {
        return ResponseEntity.ok(service.getUrlDetails(alias));
    }

    @GetMapping("/{alias}/stats")
    public ResponseEntity<UrlStatsResponse> stats(@PathVariable String alias) {
        return ResponseEntity.ok(service.getStats(alias));
    }

    @DeleteMapping("/{alias}")
    public ResponseEntity<Void> delete(@PathVariable String alias) {
        service.deleteUrl(alias);
        return ResponseEntity.noContent().build();
    }
}

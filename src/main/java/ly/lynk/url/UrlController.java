package ly.lynk.url;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
@Tag(name = "URL Management", description = "Create, retrieve, list, and delete short URLs")
@SecurityRequirement(name = "Bearer Authentication")
public class UrlController {

    private final UrlService urlService;

    @PostMapping
    @Operation(
            summary = "Create a short URL",
            description = "Generates a new short URL. If no alias is provided, a shortcode is generated automatically.")
    @ApiResponse(responseCode = "201", description = "Short URL created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request (validation errors)", content = @Content)
    @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT", content = @Content)
    public ResponseEntity<UrlResponse> createUrl(
            @Valid @RequestBody CreateUrlRequest request, JwtAuthenticationToken auth) {
        String userId = auth.getToken().getSubject();
        UrlResponse response = urlService.createUrl(request, userId);
        URI location = URI.create("/api/v1/urls/" + response.shortcode());
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(response);
    }

    @GetMapping("/{shortcode:[a-zA-Z0-9][a-zA-Z0-9-]{2,10}}")
    @Operation(
            summary = "Get a short URL by shortcode",
            description = "Retrieves the details of a short URL owned by the authenticated user.")
    @ApiResponse(responseCode = "200", description = "URL details returned")
    @ApiResponse(responseCode = "404", description = "URL not found", content = @Content)
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    public ResponseEntity<UrlResponse> getUrl(
            @Parameter(description = "The shortcode to look up") @PathVariable String shortcode,
            JwtAuthenticationToken auth) {
        String userId = auth.getToken().getSubject();
        return ResponseEntity.ok(urlService.getByShortcode(shortcode, userId));
    }

    @GetMapping
    @Operation(
            summary = "List all short URLs",
            description = "Returns a paginated list of short URLs owned by the authenticated user.")
    @ApiResponse(responseCode = "200", description = "Paginated list of URLs")
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    public ResponseEntity<Page<UrlResponse>> listUrls(JwtAuthenticationToken auth, Pageable pageable) {
        String userId = auth.getToken().getSubject();
        return ResponseEntity.ok(urlService.listUrls(userId, pageable));
    }

    @DeleteMapping("/{shortcode:[a-zA-Z0-9][a-zA-Z0-9-]{2,10}}")
    @Operation(
            summary = "Delete a short URL",
            description = "Permanently removes a short URL. Only the owner can delete their URLs.")
    @ApiResponse(responseCode = "204", description = "URL deleted successfully")
    @ApiResponse(responseCode = "404", description = "URL not found", content = @Content)
    @ApiResponse(responseCode = "403", description = "Not owner of this URL", content = @Content)
    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    public ResponseEntity<Void> deleteUrl(
            @Parameter(description = "The shortcode to delete") @PathVariable String shortcode,
            JwtAuthenticationToken auth) {
        String userId = auth.getToken().getSubject();
        urlService.deleteUrl(shortcode, userId);
        return ResponseEntity.noContent().build();
    }
}

package ly.lynk.url;

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
public class UrlController {

    private final UrlService urlService;

    @PostMapping
    public ResponseEntity<UrlResponse> createUrl(
            @Valid @RequestBody CreateUrlRequest request, JwtAuthenticationToken auth) {
        String userId = auth.getToken().getSubject();
        UrlResponse response = urlService.createUrl(request, userId);
        URI location = URI.create("/api/v1/urls/" + response.shortcode());
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(response);
    }

    @GetMapping("/{shortcode:[a-zA-Z0-9][a-zA-Z0-9-]{2,10}}")
    public ResponseEntity<UrlResponse> getUrl(@PathVariable String shortcode, JwtAuthenticationToken auth) {
        String userId = auth.getToken().getSubject();
        return ResponseEntity.ok(urlService.getByShortcode(shortcode, userId));
    }

    @GetMapping
    public ResponseEntity<Page<UrlResponse>> listUrls(JwtAuthenticationToken auth, Pageable pageable) {
        String userId = auth.getToken().getSubject();
        return ResponseEntity.ok(urlService.listUrls(userId, pageable));
    }

    @DeleteMapping("/{shortcode:[a-zA-Z0-9][a-zA-Z0-9-]{2,10}}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortcode, JwtAuthenticationToken auth) {
        String userId = auth.getToken().getSubject();
        urlService.deleteUrl(shortcode, userId);
        return ResponseEntity.noContent().build();
    }
}

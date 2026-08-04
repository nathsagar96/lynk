package ly.lynk.url;

import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    @PostMapping("/api/v1/urls")
    public ResponseEntity<UrlResponse> createUrl(
            @Valid @RequestBody CreateUrlRequest request, JwtAuthenticationToken auth) {
        String userId = auth.getToken().getSubject();
        UrlResponse response = urlService.createUrl(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/urls")
    public ResponseEntity<Page<UrlResponse>> listUrls(JwtAuthenticationToken auth, Pageable pageable) {
        String userId = auth.getToken().getSubject();
        return ResponseEntity.ok(urlService.listUrls(userId, pageable));
    }

    @DeleteMapping("/api/v1/urls/{shortcode}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortcode, JwtAuthenticationToken auth) {
        String userId = auth.getToken().getSubject();
        urlService.deleteUrl(shortcode, userId);
        return ResponseEntity.noContent().build();
    }
}

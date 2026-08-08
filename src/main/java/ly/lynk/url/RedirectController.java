package ly.lynk.url;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Redirect", description = "Resolve short URLs and redirect to original destination")
public class RedirectController {

    private final RedirectService redirectService;

    @GetMapping("/{shortcode:[a-zA-Z0-9][a-zA-Z0-9-]{2,10}}")
    @Operation(
            summary = "Redirect to original URL",
            description =
                    "Resolves a shortcode to its original URL and redirects the client (302). Tracks click analytics asynchronously.")
    @ApiResponse(responseCode = "302", description = "Redirect to original URL")
    @ApiResponse(
            responseCode = "404",
            description = "Shortcode not found",
            content = @io.swagger.v3.oas.annotations.media.Content)
    @ApiResponse(
            responseCode = "410",
            description = "URL has expired",
            content = @io.swagger.v3.oas.annotations.media.Content)
    public ResponseEntity<Void> redirect(
            @Parameter(description = "The shortcode to resolve") @PathVariable String shortcode,
            HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        String referer = request.getHeader(HttpHeaders.REFERER);

        String originalUrl = redirectService.resolveAndTrack(shortcode, ipAddress, userAgent, referer);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}

package ly.lynk.controller;

import jakarta.servlet.http.HttpServletRequest;
import ly.lynk.service.UrlShortenerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Handles the {@code /{alias}} redirect route.
 *
 * <p>This is a {@link Controller} rather than a {@code RestController} because it only ever
 * produces redirects and never writes a response body.
 */
@Controller
public class RedirectController {

    private final UrlShortenerService service;

    /**
     * Creates a redirect controller with the given URL shortener service.
     *
     * @param service the URL shortener service
     */
    public RedirectController(UrlShortenerService service) {
        this.service = service;
    }

    /**
     * Resolves the alias and redirects to the original URL.
     *
     * <p>{@code GET} records a click; {@code HEAD} resolves without recording one so that link
     * checkers and prefetchers do not inflate the statistics.
     *
     * @param alias the alias to redirect
     * @param request the incoming HTTP servlet request
     * @return 302 Found response with Location and cache-control headers
     */
    @RequestMapping(
            value = "/{alias:[a-zA-Z0-9_-]+}",
            method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Void> redirect(@PathVariable String alias, HttpServletRequest request) {
        boolean trackClick = HttpMethod.GET.matches(request.getMethod());
        String target = service.resolveAndTrack(alias, request.getQueryString(), trackClick);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.LOCATION, target)
                .build();
    }
}

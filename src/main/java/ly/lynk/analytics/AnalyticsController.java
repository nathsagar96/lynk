package ly.lynk.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Retrieve click analytics and visitor breakdowns for short URLs")
@SecurityRequirement(name = "Bearer Authentication")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/{shortcode:[a-zA-Z0-9][a-zA-Z0-9-]{2,10}}/analytics")
    @Operation(
            summary = "Get URL click analytics",
            description =
                    "Retrieves click statistics, time-series data, top referers, browsers, and OS metrics for a shortcode.")
    @ApiResponse(
            responseCode = "200",
            description = "Analytics data retrieved successfully",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AnalyticsResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid date range parameters",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "403",
            description = "Not owner of this URL",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "URL not found",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @Parameter(description = "The shortcode to get analytics for") @PathVariable String shortcode,
            @Parameter(description = "Start instant (ISO-8601), defaults to 30 days ago")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant startDate,
            @Parameter(description = "End instant (ISO-8601), defaults to current instant")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant endDate,
            JwtAuthenticationToken auth) {

        Instant now = Instant.now();
        Instant end = (endDate != null) ? endDate : now;
        Instant start = (startDate != null) ? startDate : end.minus(30, ChronoUnit.DAYS);

        String userId = auth.getToken().getSubject();
        AnalyticsResponse response = analyticsService.getAnalytics(shortcode, start, end, userId);
        return ResponseEntity.ok(response);
    }
}

package ly.lynk.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Daily click count time-series point")
public record TimeSeriesPoint(
        @Schema(description = "Date of recorded clicks (ISO-8601)", example = "2026-08-08")
        LocalDate date,

        @Schema(description = "Number of clicks on this date", example = "42")
        long clicks) {}

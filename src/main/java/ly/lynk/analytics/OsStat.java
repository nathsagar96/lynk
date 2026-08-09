package ly.lynk.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Operating system click count breakdown")
public record OsStat(
        @Schema(
                description = "Parsed operating system name (e.g. macOS, Windows, iOS, Android, Linux, Other, Unknown)",
                example = "macOS")
        String os,

        @Schema(description = "Number of clicks from this operating system", example = "90")
        long clicks) {}

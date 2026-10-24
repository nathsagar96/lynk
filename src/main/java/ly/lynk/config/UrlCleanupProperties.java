package ly.lynk.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the expired URL background cleanup job.
 *
 * @param batchSize maximum number of expired URLs to purge in a single transaction
 * @param maxBatchesPerRun maximum number of batches to process in a single execution run
 */
@Validated
@ConfigurationProperties(prefix = "app.cleanup")
public record UrlCleanupProperties(
        @Min(1) int batchSize, @Min(1) int maxBatchesPerRun) {}

package com.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.lynk.AbstractIntegrationTestBase;
import com.lynk.domain.UrlMapping;
import com.lynk.repository.UrlMappingRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("ExpiryCleanupService")
class ExpiryCleanupServiceIT extends AbstractIntegrationTestBase {

    private static final String DESTINATION = "https://example.com";

    @Autowired
    private ExpiryCleanupService cleanupService;

    @Autowired
    private UrlMappingRepository repository;

    private UrlMapping persist(String shortCode, Instant expiresAt) {
        UrlMapping mapping = new UrlMapping(DESTINATION, shortCode, expiresAt);
        return repository.saveAndFlush(mapping);
    }

    @Nested
    @DisplayName("deleteExpiredMappings")
    class DeleteExpiredMappings {

        @Test
        @DisplayName("deletes mappings whose expiry has passed and keeps the rest")
        void deleteExpiredMappings_deletesExpiredRows_whenExpiryHasPassed() {
            // Arrange
            persist("sweep-expired", Instant.now().minus(1, ChronoUnit.HOURS));
            UrlMapping alive = persist("sweep-future", Instant.now().plus(1, ChronoUnit.HOURS));

            // Act
            cleanupService.deleteExpiredMappings();

            // Assert
            assertAll(
                    () -> assertThat(repository.findByShortCode("sweep-expired"))
                            .isEmpty(),
                    () -> assertThat(repository.findById(alive.getId())).isPresent());
        }

        @Test
        @DisplayName("keeps mappings that never expire")
        void deleteExpiredMappings_keepsMappingsWithoutExpiry_whenExpiresAtIsNull() {
            // Arrange
            UrlMapping permanent = persist("sweep-permanent", null);

            // Act
            cleanupService.deleteExpiredMappings();

            // Assert
            assertThat(repository.findById(permanent.getId())).isPresent();
        }

        @Test
        @DisplayName("is safe to run repeatedly with nothing left to delete")
        void deleteExpiredMappings_doesNotThrow_whenRunTwiceWithNothingToDelete() {
            // Arrange
            persist("sweep-idempotent", Instant.now().minus(1, ChronoUnit.HOURS));
            cleanupService.deleteExpiredMappings();

            // Act & Assert
            assertThatNoException().isThrownBy(cleanupService::deleteExpiredMappings);
            assertThat(repository.findByShortCode("sweep-idempotent")).isEmpty();
        }
    }
}

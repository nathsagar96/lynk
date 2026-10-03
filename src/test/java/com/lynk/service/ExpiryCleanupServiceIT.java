package com.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.lynk.AbstractIntegrationTestBase;
import com.lynk.domain.UrlMapping;
import com.lynk.repository.UrlMappingRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
    class DeleteExpiredMappings {

        @Test
        void deleteExpiredMappings_deletesExpiredRows_whenExpiryHasPassed() {
            persist("sweep-expired", Instant.now().minus(1, ChronoUnit.HOURS));
            UrlMapping alive = persist("sweep-future", Instant.now().plus(1, ChronoUnit.HOURS));

            cleanupService.deleteExpiredMappings();

            assertAll(
                    () -> assertThat(repository.findByShortCode("sweep-expired"))
                            .isEmpty(),
                    () -> assertThat(repository.findById(alive.getId())).isPresent());
        }

        @Test
        void deleteExpiredMappings_keepsMappingsWithoutExpiry_whenExpiresAtIsNull() {
            UrlMapping permanent = persist("sweep-permanent", null);

            cleanupService.deleteExpiredMappings();

            assertThat(repository.findById(permanent.getId())).isPresent();
        }

        @Test
        void deleteExpiredMappings_doesNotThrow_whenRunTwiceWithNothingToDelete() {
            persist("sweep-idempotent", Instant.now().minus(1, ChronoUnit.HOURS));
            cleanupService.deleteExpiredMappings();

            assertThatNoException().isThrownBy(cleanupService::deleteExpiredMappings);
            assertThat(repository.findByShortCode("sweep-idempotent")).isEmpty();
        }
    }
}

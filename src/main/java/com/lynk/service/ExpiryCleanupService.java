package com.lynk.service;

import com.lynk.repository.UrlMappingRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly sweep that deletes mappings whose expiry moment has passed.
 * <p>
 * Expiry is enforced on the read path regardless, so this job only reclaims storage; the service
 * is correct even if the schedule never fires.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpiryCleanupService {

    private final UrlMappingRepository repository;

    @Scheduled(cron = "${lynk.cleanup.cron}")
    @Transactional
    public void deleteExpiredMappings() {
        int deleted = repository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Expiry cleanup removed {} expired URL mapping(s)", deleted);
        }
    }
}

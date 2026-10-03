package com.lynk.repository;

import com.lynk.domain.UrlMapping;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /**
     * Atomically bumps the click counter for a short code.
     * <p>
     * Deliberately not an entity load plus increment plus save: redirects are the hot path and this
     * keeps them to a single column update, correct under concurrency. {@code flushAutomatically}
     * pushes any pending changes before the update and {@code clearAutomatically} evicts the
     * mapping read earlier in the same transaction, so a later flush cannot overwrite the count
     * with a stale value.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UrlMapping m set m.clickCount = m.clickCount + 1 where m.shortCode = :shortCode")
    void incrementClickCount(@Param("shortCode") String shortCode);

    @Modifying
    @Query("delete from UrlMapping m where m.expiresAt is not null and m.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}

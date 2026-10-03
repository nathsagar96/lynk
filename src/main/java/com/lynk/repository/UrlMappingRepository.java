package com.lynk.repository;

import com.lynk.domain.UrlMapping;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortCode(String shortCode);

    /**
     * Reads a mapping only for the subject that owns it.
     * <p>
     * This is what makes a link private: {@code GET /{shortCode}} resolves it for anybody, but stats
     * are scoped to the owner. It also cannot match a row with a NULL {@code owner}, which
     * is why a link registered before ownership existed reports as missing rather than as readable
     * by everyone.
     */
    Optional<UrlMapping> findByShortCodeAndOwner(String shortCode, String owner);

    /**
     * One page of a single owner's links, newest first.
     * <p>
     * {@code created_at} alone does not order a page deterministically, because two links registered
     * in the same transaction share a timestamp and the page boundary would then fall between them
     * unpredictably; the id tiebreaker makes paging stable.
     */
    Page<UrlMapping> findByOwnerOrderByCreatedAtDescIdDesc(String owner, Pageable pageable);

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

package ly.lynk.repository;

import java.time.Instant;
import java.util.Optional;
import ly.lynk.model.UrlMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Repository for persisting and querying {@link UrlMapping} entities. */
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    /**
     * Finds a URL mapping by its alias.
     *
     * @param alias the alias to look up
     * @return an {@link Optional} containing the mapping if found, or empty otherwise
     */
    Optional<UrlMapping> findByAlias(String alias);

    /**
     * Finds all URL mappings owned by the given owner ID with pagination.
     *
     * @param ownerId the owner subject ID
     * @param pageable pagination parameters
     * @return a page of matching URL mappings
     */
    Page<UrlMapping> findByOwnerId(String ownerId, Pageable pageable);

    /**
     * Atomically increments the click count for the given alias.
     *
     * @param alias the alias whose click count is to be incremented
     * @return the number of updated rows (1 if found, 0 otherwise)
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UrlMapping u SET u.clickCount = u.clickCount + 1 WHERE u.alias = :alias")
    int incrementClickCount(@Param("alias") String alias);

    /**
     * Deletes a batch of expired URL mappings up to {@code batchSize}.
     *
     * <p>Uses a common table expression with {@code FOR UPDATE SKIP LOCKED} to prevent
     * blocking concurrent queries and lock contention.
     *
     * @param now the reference expiration threshold
     * @param batchSize the maximum number of rows to delete in this batch
     * @return the number of rows actually deleted
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
                WITH expired AS (
                    SELECT id
                    FROM url_mappings
                    WHERE expires_at IS NOT NULL AND expires_at <= :now
                    ORDER BY expires_at ASC
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                )
                DELETE FROM url_mappings u
                USING expired e
                WHERE u.id = e.id
                """, nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}

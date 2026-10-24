package ly.lynk.repository;

import java.util.Optional;
import ly.lynk.model.UrlMapping;
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
     * Atomically increments the click count for the given alias.
     *
     * @param alias the alias whose click count is to be incremented
     * @return the number of updated rows (1 if found, 0 otherwise)
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UrlMapping u SET u.clickCount = u.clickCount + 1 WHERE u.alias = :alias")
    int incrementClickCount(@Param("alias") String alias);
}

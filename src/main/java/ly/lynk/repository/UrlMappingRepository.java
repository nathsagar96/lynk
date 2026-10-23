package ly.lynk.repository;

import java.util.Optional;
import ly.lynk.model.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByAlias(String alias);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UrlMapping u SET u.clickCount = u.clickCount + 1 WHERE u.alias = :alias")
    int incrementClickCount(@Param("alias") String alias);
}

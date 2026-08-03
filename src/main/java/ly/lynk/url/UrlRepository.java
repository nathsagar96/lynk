package ly.lynk.url;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlRepository extends JpaRepository<UrlEntity, Long> {

    Optional<UrlEntity> findByShortcode(String shortcode);

    Page<UrlEntity> findByUserId(String userId, Pageable pageable);

    boolean existsByShortcode(String shortcode);
}

package ly.lynk.click;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickRepository extends JpaRepository<ClickEntity, Long> {

    List<ClickEntity> findByShortcodeAndClickedAtBetween(String shortcode, Instant startDate, Instant endDate);
}

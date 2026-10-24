package ly.lynk.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ly.lynk.TestSecurityConfiguration;
import ly.lynk.TestcontainersConfiguration;
import ly.lynk.service.UrlCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        properties = {"app.cleanup.cron=-", "app.cleanup.lock-at-most-for=10m", "app.cleanup.lock-at-least-for=10s"})
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
class UrlCleanupSchedulerLockIT {

    @Autowired
    private UrlCleanupScheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UrlCleanupService cleanupService;

    @Test
    @DisplayName("acquires lock in database and skips duplicate concurrent executions")
    void acquiresLockAndSkipsConcurrentRun() {
        // First execution acquires lock and invokes cleanup
        scheduler.scheduleCleanup();
        verify(cleanupService, times(1)).purgeExpiredUrls();

        Integer lockCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shedlock WHERE name = 'UrlCleanupScheduler_purgeExpiredUrls'", Integer.class);
        assertThat(lockCount).isEqualTo(1);

        // Second immediate execution within lockAtLeastFor window is skipped
        scheduler.scheduleCleanup();
        verify(cleanupService, times(1)).purgeExpiredUrls();
    }
}

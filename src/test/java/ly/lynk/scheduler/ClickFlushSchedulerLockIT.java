package ly.lynk.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ly.lynk.TestSecurityConfiguration;
import ly.lynk.TestcontainersConfiguration;
import ly.lynk.service.ClickFlushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        properties = {
            "app.clicks.flush-interval=10m",
            "app.clicks.lock-at-most-for=10m",
            "app.clicks.lock-at-least-for=10s"
        })
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
class ClickFlushSchedulerLockIT {

    @Autowired
    private ClickFlushScheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ClickFlushService clickFlushService;

    @Test
    @DisplayName("acquires lock in database and skips duplicate concurrent executions")
    void acquiresLockAndSkipsConcurrentRun() {
        scheduler.scheduleFlush();
        verify(clickFlushService, times(1)).flushPendingClicks();

        Integer lockCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shedlock WHERE name = 'ClickFlushScheduler_flushClicks'", Integer.class);
        assertThat(lockCount).isEqualTo(1);

        // Immediate duplicate execution within lockAtLeastFor window is skipped
        scheduler.scheduleFlush();
        verify(clickFlushService, times(1)).flushPendingClicks();
    }
}

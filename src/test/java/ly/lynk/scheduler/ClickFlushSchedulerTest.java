package ly.lynk.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ly.lynk.service.ClickFlushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClickFlushSchedulerTest {

    @Test
    @DisplayName("invokes click flush service on schedule")
    void delegatesToClickFlushService() {
        ClickFlushService service = mock(ClickFlushService.class);
        ClickFlushScheduler scheduler = new ClickFlushScheduler(service);

        scheduler.scheduleFlush();

        verify(service).flushPendingClicks();
    }
}

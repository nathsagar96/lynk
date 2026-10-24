package ly.lynk.scheduler;

import static org.mockito.Mockito.verify;

import ly.lynk.service.UrlCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlCleanupSchedulerTest {

    @Mock
    private UrlCleanupService cleanupService;

    @InjectMocks
    private UrlCleanupScheduler scheduler;

    @Test
    @DisplayName("delegates execution to the cleanup service")
    void delegatesToService() {
        scheduler.scheduleCleanup();

        verify(cleanupService).purgeExpiredUrls();
    }
}

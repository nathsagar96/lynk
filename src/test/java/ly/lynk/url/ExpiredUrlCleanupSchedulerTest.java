package ly.lynk.url;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ExpiredUrlCleanupSchedulerTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private UrlCacheService urlCacheService;

    @Mock
    private UrlProperties urlProperties;

    private static final Instant FIXED_TIME = Instant.parse("2026-08-09T00:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    private ExpiredUrlCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ExpiredUrlCleanupScheduler(urlRepository, urlCacheService, urlProperties, fixedClock);
    }

    @Test
    void shouldDeleteExpiredUrlsAndEvictCache() {
        var entity1 = UrlEntity.builder()
                .id(1L)
                .shortcode("abc")
                .originalUrl("https://example.com")
                .userId("user-1")
                .expiresAt(FIXED_TIME.minusSeconds(3600))
                .createdAt(FIXED_TIME.minusSeconds(7200))
                .build();
        var entity2 = UrlEntity.builder()
                .id(2L)
                .shortcode("def")
                .originalUrl("https://expired.com")
                .userId("user-2")
                .expiresAt(FIXED_TIME.minusSeconds(1800))
                .createdAt(FIXED_TIME.minusSeconds(3600))
                .build();

        when(urlProperties.cleanup()).thenReturn(new UrlProperties.Cleanup(100));
        when(urlRepository.findTopByExpiresAtBeforeOrderByExpiresAtAsc(eq(FIXED_TIME), any(PageRequest.class)))
                .thenReturn(List.of(entity1, entity2));
        when(urlRepository.deleteByExpiresAtBefore(FIXED_TIME)).thenReturn(2L);

        scheduler.deleteExpiredUrls();

        verify(urlCacheService).evict("abc");
        verify(urlCacheService).evict("def");
        verify(urlRepository).deleteByExpiresAtBefore(FIXED_TIME);
    }

    @Test
    void shouldNotDeleteWhenNoExpiredUrlsExist() {
        when(urlProperties.cleanup()).thenReturn(new UrlProperties.Cleanup(100));
        when(urlRepository.findTopByExpiresAtBeforeOrderByExpiresAtAsc(eq(FIXED_TIME), any(PageRequest.class)))
                .thenReturn(List.of());

        scheduler.deleteExpiredUrls();

        verify(urlCacheService, never()).evict(any());
        verify(urlRepository, never()).deleteByExpiresAtBefore(any());
    }

    @Test
    void shouldProcessMultipleBatches() {
        var entity = UrlEntity.builder()
                .id(1L)
                .shortcode("abc")
                .originalUrl("https://example.com")
                .userId("user-1")
                .expiresAt(FIXED_TIME.minusSeconds(3600))
                .createdAt(FIXED_TIME.minusSeconds(7200))
                .build();

        when(urlProperties.cleanup()).thenReturn(new UrlProperties.Cleanup(2));
        when(urlRepository.findTopByExpiresAtBeforeOrderByExpiresAtAsc(eq(FIXED_TIME), any(PageRequest.class)))
                .thenReturn(List.of(entity))
                .thenReturn(List.of(entity))
                .thenReturn(List.of());
        when(urlRepository.deleteByExpiresAtBefore(FIXED_TIME)).thenReturn(2L).thenReturn(2L);

        scheduler.deleteExpiredUrls();

        verify(urlCacheService, times(2)).evict("abc");
        verify(urlRepository, times(2)).deleteByExpiresAtBefore(FIXED_TIME);
    }

    @Test
    void shouldStopWhenBatchSmallerThanBatchSize() {
        var entity = UrlEntity.builder()
                .id(1L)
                .shortcode("abc")
                .originalUrl("https://example.com")
                .userId("user-1")
                .expiresAt(FIXED_TIME.minusSeconds(3600))
                .createdAt(FIXED_TIME.minusSeconds(7200))
                .build();

        when(urlProperties.cleanup()).thenReturn(new UrlProperties.Cleanup(500));
        when(urlRepository.findTopByExpiresAtBeforeOrderByExpiresAtAsc(eq(FIXED_TIME), any(PageRequest.class)))
                .thenReturn(List.of(entity));
        when(urlRepository.deleteByExpiresAtBefore(FIXED_TIME)).thenReturn(1L);

        scheduler.deleteExpiredUrls();

        verify(urlCacheService).evict("abc");
        verify(urlRepository, times(1)).deleteByExpiresAtBefore(FIXED_TIME);
    }
}

package ly.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import ly.lynk.config.UrlCleanupProperties;
import ly.lynk.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlCleanupServiceTest {

    @Mock
    private UrlMappingRepository repository;

    private final Instant now = Instant.parse("2026-10-24T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private UrlCleanupProperties properties;
    private UrlCleanupService service;

    @BeforeEach
    void setUp() {
        properties = new UrlCleanupProperties(100, 3);
        service = new UrlCleanupService(repository, properties, clock);
    }

    @Test
    @DisplayName("returns zero and exits immediately when no records are expired")
    void returnsZeroWhenNoRecordsExpired() {
        given(repository.deleteExpiredBatch(now, 100)).willReturn(0);

        int totalDeleted = service.purgeExpiredUrls();

        assertThat(totalDeleted).isZero();
        verify(repository).deleteExpiredBatch(now, 100);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("stops when a batch returns fewer rows than the batch size")
    void stopsWhenBatchLessThanBatchSize() {
        given(repository.deleteExpiredBatch(now, 100)).willReturn(42);

        int totalDeleted = service.purgeExpiredUrls();

        assertThat(totalDeleted).isEqualTo(42);
        verify(repository).deleteExpiredBatch(now, 100);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("loops across multiple batches until partial batch is reached")
    void loopsAcrossMultipleBatches() {
        given(repository.deleteExpiredBatch(now, 100)).willReturn(100).willReturn(50);

        int totalDeleted = service.purgeExpiredUrls();

        assertThat(totalDeleted).isEqualTo(150);
        verify(repository, Mockito.times(2)).deleteExpiredBatch(now, 100);
    }

    @Test
    @DisplayName("halts when maxBatchesPerRun limit is reached even if more rows remain")
    void haltsWhenMaxBatchesReached() {
        given(repository.deleteExpiredBatch(now, 100))
                .willReturn(100)
                .willReturn(100)
                .willReturn(100);

        int totalDeleted = service.purgeExpiredUrls();

        assertThat(totalDeleted).isEqualTo(300);
        verify(repository, Mockito.times(3)).deleteExpiredBatch(now, 100);
        verifyNoMoreInteractions(repository);
    }
}

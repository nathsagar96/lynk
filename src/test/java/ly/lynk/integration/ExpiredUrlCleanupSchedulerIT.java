package ly.lynk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import ly.lynk.TestcontainersConfiguration;
import ly.lynk.url.ExpiredUrlCleanupScheduler;
import ly.lynk.url.UrlEntity;
import ly.lynk.url.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExpiredUrlCleanupSchedulerIT {

    @Autowired
    private ExpiredUrlCleanupScheduler scheduler;

    @Autowired
    private UrlRepository urlRepository;

    @BeforeEach
    void setUp() {
        urlRepository.deleteAll();
    }

    @Test
    void shouldDeleteExpiredUrls() {
        var expiredEntity = UrlEntity.builder()
                .id(1L)
                .shortcode("expired")
                .originalUrl("https://expired.com")
                .userId("user-1")
                .expiresAt(Instant.now().minusSeconds(3600))
                .createdAt(Instant.now().minusSeconds(7200))
                .build();

        var activeEntity = UrlEntity.builder()
                .id(2L)
                .shortcode("active")
                .originalUrl("https://active.com")
                .userId("user-1")
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();

        urlRepository.save(expiredEntity);
        urlRepository.save(activeEntity);

        scheduler.deleteExpiredUrls();

        assertThat(urlRepository.findByShortcode("expired")).isEmpty();
        assertThat(urlRepository.findByShortcode("active")).isPresent();
    }

    @Test
    void shouldNotDeleteWhenNoExpiredUrls() {
        var activeEntity = UrlEntity.builder()
                .id(1L)
                .shortcode("active")
                .originalUrl("https://active.com")
                .userId("user-1")
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();

        urlRepository.save(activeEntity);

        scheduler.deleteExpiredUrls();

        assertThat(urlRepository.findByShortcode("active")).isPresent();
        assertThat(urlRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldDeleteMultipleExpiredUrlsInBatches() {
        for (int i = 0; i < 5; i++) {
            urlRepository.save(UrlEntity.builder()
                    .id((long) (i + 1))
                    .shortcode("expired-" + i)
                    .originalUrl("https://expired" + i + ".com")
                    .userId("user-1")
                    .expiresAt(Instant.now().minusSeconds(3600 + i))
                    .createdAt(Instant.now().minusSeconds(7200 + i))
                    .build());
        }

        urlRepository.save(UrlEntity.builder()
                .id(6L)
                .shortcode("active")
                .originalUrl("https://active.com")
                .userId("user-1")
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build());

        scheduler.deleteExpiredUrls();

        assertThat(urlRepository.count()).isEqualTo(1);
        assertThat(urlRepository.findByShortcode("active")).isPresent();
    }
}

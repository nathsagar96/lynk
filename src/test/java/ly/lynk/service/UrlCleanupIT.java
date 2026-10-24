package ly.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import ly.lynk.TestcontainersConfiguration;
import ly.lynk.config.UrlCleanupProperties;
import ly.lynk.model.UrlMapping;
import ly.lynk.repository.UrlMappingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class UrlCleanupIT {

    @Autowired
    private UrlMappingRepository repository;

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("purges all expired URLs across multiple batches end-to-end")
    void purgesExpiredAcrossMultipleBatches() {
        Instant now = Instant.parse("2026-10-24T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        // 5 expired, 1 future, 1 permanent
        repository.saveAndFlush(new UrlMapping("https://e1.example", "e1", CREATED_AT, now.minusSeconds(50)));
        repository.saveAndFlush(new UrlMapping("https://e2.example", "e2", CREATED_AT, now.minusSeconds(40)));
        repository.saveAndFlush(new UrlMapping("https://e3.example", "e3", CREATED_AT, now.minusSeconds(30)));
        repository.saveAndFlush(new UrlMapping("https://e4.example", "e4", CREATED_AT, now.minusSeconds(20)));
        repository.saveAndFlush(new UrlMapping("https://e5.example", "e5", CREATED_AT, now.minusSeconds(10)));
        repository.saveAndFlush(new UrlMapping("https://fut.example", "fut", CREATED_AT, now.plusSeconds(3600)));
        repository.saveAndFlush(new UrlMapping("https://perm.example", "perm", CREATED_AT, null));

        // Batch size of 2, max 10 batches -> will take 3 batches (2, 2, 1)
        UrlCleanupProperties properties = new UrlCleanupProperties(2, 10);
        UrlCleanupService cleanupService = new UrlCleanupService(repository, properties, clock);

        int totalDeleted = cleanupService.purgeExpiredUrls();

        assertThat(totalDeleted).isEqualTo(5);
        assertThat(repository.findByAlias("e1")).isEmpty();
        assertThat(repository.findByAlias("e2")).isEmpty();
        assertThat(repository.findByAlias("e3")).isEmpty();
        assertThat(repository.findByAlias("e4")).isEmpty();
        assertThat(repository.findByAlias("e5")).isEmpty();
        assertThat(repository.findByAlias("fut")).isPresent();
        assertThat(repository.findByAlias("perm")).isPresent();
    }
}

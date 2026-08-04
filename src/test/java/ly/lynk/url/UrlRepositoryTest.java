package ly.lynk.url;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import ly.lynk.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UrlRepositoryTest {

    @Autowired
    private UrlRepository urlRepository;

    private static final AtomicLong ID_SEQUENCE = new AtomicLong(1);

    @Test
    void shouldFindByShortcode() {
        UrlEntity saved = saveUrl("abc", "https://example.com", "user-1");

        Optional<UrlEntity> found = urlRepository.findByShortcode("abc");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getOriginalUrl()).isEqualTo("https://example.com");
    }

    @Test
    void shouldReturnEmptyWhenShortcodeDoesNotExist() {
        assertThat(urlRepository.findByShortcode("missing")).isEmpty();
    }

    @Test
    void shouldReturnPagedUrlsForUser() {
        saveUrl("aaa", "https://example1.com", "user-1");
        saveUrl("bbb", "https://example2.com", "user-1");
        saveUrl("ccc", "https://example3.com", "user-1");
        saveUrl("ddd", "https://other.com", "user-2");

        Page<UrlEntity> firstPage = urlRepository.findByUserId("user-1", PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getContent()).extracting(UrlEntity::getShortcode).containsExactlyInAnyOrder("aaa", "bbb");

        Page<UrlEntity> secondPage = urlRepository.findByUserId("user-1", PageRequest.of(1, 2));

        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.getContent()).extracting(UrlEntity::getShortcode).containsExactly("ccc");
    }

    @Test
    void shouldReturnEmptyPageWhenUserHasNoUrls() {
        assertThat(urlRepository.findByUserId("user-1", PageRequest.of(0, 10)).getContent())
                .isEmpty();
    }

    @Test
    void shouldReturnTrueWhenShortcodeExists() {
        saveUrl("abc", "https://example.com", "user-1");

        assertThat(urlRepository.existsByShortcode("abc")).isTrue();
    }

    @Test
    void shouldReturnFalseWhenShortcodeDoesNotExist() {
        assertThat(urlRepository.existsByShortcode("missing")).isFalse();
    }

    private UrlEntity saveUrl(String shortcode, String originalUrl, String userId) {
        Instant now = Instant.now();
        UrlEntity entity = UrlEntity.builder()
                .id(ID_SEQUENCE.getAndIncrement())
                .shortcode(shortcode)
                .originalUrl(originalUrl)
                .userId(userId)
                .expiresAt(now.plusSeconds(3600))
                .createdAt(now)
                .build();
        return urlRepository.save(entity);
    }
}

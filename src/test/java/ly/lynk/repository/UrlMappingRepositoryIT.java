package ly.lynk.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import ly.lynk.TestcontainersConfiguration;
import ly.lynk.model.UrlMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class UrlMappingRepositoryIT {

    @Autowired
    private UrlMappingRepository repository;

    private UrlMapping mapping(String alias, String originalUrl, Instant expiresAt) {
        return repository.saveAndFlush(new UrlMapping(originalUrl, alias, Instant.now(), expiresAt));
    }

    @Test
    @DisplayName("a mapping can be stored and found by its alias")
    void findsByAlias() {
        mapping("spring", "https://spring.io", null);

        Optional<UrlMapping> found = repository.findByAlias("spring");

        assertThat(found).isPresent();
        assertThat(found.get().getOriginalUrl()).isEqualTo("https://spring.io");
        assertThat(found.get().getClickCount()).isZero();
    }

    @Test
    @DisplayName("the alias uniqueness constraint is enforced")
    void enforcesUniqueAlias() {
        mapping("spring", "https://spring.io", null);

        assertThatThrownBy(() -> mapping("spring", "https://other.example", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the click count is incremented atomically")
    void incrementsClickCount() {
        mapping("spring", "https://spring.io", null);

        assertThat(repository.incrementClickCount("spring")).isEqualTo(1);
        assertThat(repository.incrementClickCount("spring")).isEqualTo(1);
        assertThat(repository.incrementClickCount("spring")).isEqualTo(1);

        assertThat(repository.findByAlias("spring").orElseThrow().getClickCount())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("incrementing an unknown alias affects no rows")
    void incrementsNothingForUnknownAlias() {
        assertThat(repository.incrementClickCount("missing")).isZero();
    }

    @Test
    @DisplayName("a mapping can be deleted by its alias")
    void deletesByAlias() {
        mapping("spring", "https://spring.io", null);

        UrlMapping saved = repository.findByAlias("spring").orElseThrow();
        repository.delete(saved);

        assertThat(repository.findByAlias("spring")).isEmpty();
    }
}

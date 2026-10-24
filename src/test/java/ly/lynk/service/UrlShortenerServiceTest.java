package ly.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import ly.lynk.config.AppProperties;
import ly.lynk.dto.ShortenUrlRequest;
import ly.lynk.dto.ShortenUrlResponse;
import ly.lynk.dto.UrlStatsResponse;
import ly.lynk.exception.AliasAlreadyExistsException;
import ly.lynk.exception.AliasExpiredException;
import ly.lynk.exception.AliasGenerationException;
import ly.lynk.exception.AliasNotFoundException;
import ly.lynk.exception.InvalidUrlException;
import ly.lynk.exception.ReservedAliasException;
import ly.lynk.model.UrlMapping;
import ly.lynk.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String BASE_URL = "http://localhost:8080";

    @Mock
    private UrlMappingRepository repository;

    @Mock
    private UrlMappingWriter writer;

    @Mock
    private AliasGenerator aliasGenerator;

    private UrlShortenerService service;
    private AppProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AppProperties(BASE_URL, 7, 3, Set.of("api", "actuator"));
        service = new UrlShortenerService(
                repository, writer, aliasGenerator, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private UrlMapping mapping(String alias, String originalUrl, Instant expiresAt) {
        return new UrlMapping(originalUrl, alias, NOW, expiresAt);
    }

    @Test
    @DisplayName("the short URL is built from the configured base URL")
    void buildsShortUrlFromConfiguredBaseUrl() {
        properties = new AppProperties("https://lynk.dev", 7, 3, Set.of("api", "actuator"));
        service = new UrlShortenerService(
                repository, writer, aliasGenerator, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(writer.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortenUrlResponse response = service.shortenUrl(new ShortenUrlRequest("https://spring.io", "spring", null));

        assertThat(response.shortUrl()).isEqualTo("https://lynk.dev/spring");
    }

    @Test
    @DisplayName("a trailing slash on the base URL does not produce a double slash")
    void normalizesTrailingSlashOnBaseUrl() {
        properties = new AppProperties("https://lynk.dev/", 7, 3, Set.of("api", "actuator"));
        service = new UrlShortenerService(
                repository, writer, aliasGenerator, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(writer.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortenUrlResponse response = service.shortenUrl(new ShortenUrlRequest("https://spring.io", "spring", null));

        assertThat(response.shortUrl()).isEqualTo("https://lynk.dev/spring");
    }

    @Test
    @DisplayName("a custom alias is stored verbatim and returned with the short URL")
    void shortensWithCustomAlias() {
        when(writer.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortenUrlResponse response = service.shortenUrl(new ShortenUrlRequest("https://spring.io", "spring", null));

        assertThat(response.alias()).isEqualTo("spring");
        assertThat(response.originalUrl()).isEqualTo("https://spring.io");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/spring");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.expiresAt()).isNull();
        verify(writer).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("the original URL is trimmed before being stored")
    void trimsOriginalUrl() {
        when(writer.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortenUrlResponse response = service.shortenUrl(new ShortenUrlRequest("  https://spring.io  ", null, null));

        assertThat(response.originalUrl()).isEqualTo("https://spring.io");
    }

    @Test
    @DisplayName("an auto-generated alias is used when no custom alias is supplied")
    void shortensWithGeneratedAlias() {
        when(aliasGenerator.generate(7)).thenReturn("a1b2c3d");
        when(writer.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortenUrlResponse response = service.shortenUrl(new ShortenUrlRequest("https://spring.io", null, null));

        assertThat(response.alias()).isEqualTo("a1b2c3d");
        verify(aliasGenerator).generate(7);
    }

    @Test
    @DisplayName("a reserved alias is rejected")
    void rejectsReservedAlias() {
        assertThatThrownBy(() -> service.shortenUrl(new ShortenUrlRequest("https://spring.io", "api", null)))
                .isInstanceOf(ReservedAliasException.class);
        verify(writer, never()).save(any());
    }

    @Test
    @DisplayName("a duplicate custom alias is rejected with a conflict")
    void rejectsExistingAlias() {
        when(writer.save(any(UrlMapping.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.shortenUrl(new ShortenUrlRequest("https://spring.io", "race", null)))
                .isInstanceOf(AliasAlreadyExistsException.class);
    }

    @Test
    @DisplayName("a collision on a generated alias triggers a retry with a new alias")
    void retriesOnGeneratedAliasCollision() {
        when(aliasGenerator.generate(7)).thenReturn("collide", "unique01");
        when(writer.save(any(UrlMapping.class)))
                .thenThrow(new DataIntegrityViolationException("dup"))
                .thenAnswer(inv -> inv.getArgument(0));

        ShortenUrlResponse response = service.shortenUrl(new ShortenUrlRequest("https://spring.io", null, null));

        assertThat(response.alias()).isEqualTo("unique01");
        verify(aliasGenerator, times(2)).generate(7);
    }

    @Test
    @DisplayName("exhausting the retry budget fails with an alias generation error")
    void failsWhenRetriesAreExhausted() {
        when(aliasGenerator.generate(7)).thenReturn("collide");
        when(writer.save(any(UrlMapping.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.shortenUrl(new ShortenUrlRequest("https://spring.io", null, null)))
                .isInstanceOf(AliasGenerationException.class);
        verify(aliasGenerator, times(3)).generate(7);
    }

    @Test
    @DisplayName("a URL pointing back to its own short URL is rejected")
    void rejectsSelfReferencingUrl() {
        assertThatThrownBy(() -> service.shortenUrl(new ShortenUrlRequest("http://localhost:8080/self", "self", null)))
                .isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() -> service.shortenUrl(new ShortenUrlRequest("http://localhost:8080/self/", "self", null)))
                .isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() ->
                        service.shortenUrl(new ShortenUrlRequest("http://localhost:8080/self?query=1", "self", null)))
                .isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() ->
                        service.shortenUrl(new ShortenUrlRequest("http://localhost:8080/self#frag", "self", null)))
                .isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() ->
                        service.shortenUrl(new ShortenUrlRequest("http://localhost:8080/without-alias", null, null)))
                .isInstanceOf(InvalidUrlException.class);
        verify(writer, never()).save(any());
    }

    @Test
    @DisplayName("an auto-generated candidate that matches a reserved alias is skipped")
    void skipsReservedGeneratedCandidate() {
        when(aliasGenerator.generate(7)).thenReturn("api", "unique01");
        when(writer.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortenUrlResponse response = service.shortenUrl(new ShortenUrlRequest("https://spring.io", null, null));

        assertThat(response.alias()).isEqualTo("unique01");
        verify(aliasGenerator, times(2)).generate(7);
    }

    @Test
    @DisplayName("a custom alias that matches a reserved alias case-insensitively is rejected")
    void rejectsCaseInsensitiveReservedAlias() {
        assertThatThrownBy(() -> service.shortenUrl(new ShortenUrlRequest("https://spring.io", "API", null)))
                .isInstanceOf(ReservedAliasException.class);
        assertThatThrownBy(() -> service.shortenUrl(new ShortenUrlRequest("https://spring.io", "Actuator", null)))
                .isInstanceOf(ReservedAliasException.class);
    }

    @Test
    @DisplayName("the expiration date is stored and returned when shortening a URL")
    void shortensWithExpirationDate() {
        Instant expiresAt = NOW.plusSeconds(3600);
        when(writer.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortenUrlResponse response =
                service.shortenUrl(new ShortenUrlRequest("https://spring.io", "spring", expiresAt));

        assertThat(response.alias()).isEqualTo("spring");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("GET resolution increments the click count and returns the target URL")
    void resolvesAndTracksClick() {
        when(repository.findByAlias("spring")).thenReturn(Optional.of(mapping("spring", "https://spring.io", null)));

        String target = service.resolveAndTrack("spring", null, true);

        assertThat(target).isEqualTo("https://spring.io");
        verify(repository).incrementClickCount("spring");
    }

    @Test
    @DisplayName("HEAD resolution does not increment the click count")
    void resolvesWithoutTrackingClick() {
        when(repository.findByAlias("spring")).thenReturn(Optional.of(mapping("spring", "https://spring.io", null)));

        String target = service.resolveAndTrack("spring", null, false);

        assertThat(target).isEqualTo("https://spring.io");
        verify(repository, never()).incrementClickCount(anyString());
    }

    @Test
    @DisplayName("incoming query parameters are forwarded to the target URL")
    void forwardsQueryParameters() {
        when(repository.findByAlias("spring")).thenReturn(Optional.of(mapping("spring", "https://spring.io", null)));

        String target = service.resolveAndTrack("spring", "utm_source=twitter", true);

        assertThat(target).isEqualTo("https://spring.io?utm_source=twitter");
    }

    @Test
    @DisplayName("incoming query parameters are merged with the target's existing query")
    void mergesQueryParameters() {
        when(repository.findByAlias("spring"))
                .thenReturn(Optional.of(mapping("spring", "https://spring.io/docs?lang=en", null)));

        String target = service.resolveAndTrack("spring", "utm_source=twitter", true);

        assertThat(target).isEqualTo("https://spring.io/docs?lang=en&utm_source=twitter");
    }

    @Test
    @DisplayName("query parameter encoding is preserved without double encoding")
    void preservesQueryEncoding() {
        when(repository.findByAlias("spring"))
                .thenReturn(Optional.of(mapping("spring", "https://spring.io/docs?lang=en%20US", null)));

        String target = service.resolveAndTrack("spring", "q=a%20b", true);

        assertThat(target).isEqualTo("https://spring.io/docs?lang=en%20US&q=a%20b");
    }

    @Test
    @DisplayName("a malformed percent escape in the incoming query is forwarded, not rejected")
    void forwardsMalformedPercentQuery() {
        when(repository.findByAlias("spring"))
                .thenReturn(Optional.of(mapping("spring", "https://spring.io/docs?lang=en", null)));

        String target = service.resolveAndTrack("spring", "discount=50%", true);

        assertThat(target).isEqualTo("https://spring.io/docs?lang=en&discount=50%");
    }

    @Test
    @DisplayName("an incoming query is inserted before the target's fragment")
    void keepsFragmentLast() {
        when(repository.findByAlias("spring"))
                .thenReturn(Optional.of(mapping("spring", "https://spring.io/docs#intro", null)));

        String target = service.resolveAndTrack("spring", "utm_source=twitter", true);

        assertThat(target).isEqualTo("https://spring.io/docs?utm_source=twitter#intro");
    }

    @Test
    @DisplayName("an unknown alias is reported as not found")
    void rejectsUnknownAlias() {
        when(repository.findByAlias("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAndTrack("missing", null, true))
                .isInstanceOf(AliasNotFoundException.class);
    }

    @Test
    @DisplayName("an expired alias cannot be resolved")
    void rejectsExpiredAlias() {
        Instant past = NOW.minusSeconds(60);
        when(repository.findByAlias("old")).thenReturn(Optional.of(mapping("old", "https://spring.io", past)));

        assertThatThrownBy(() -> service.resolveAndTrack("old", null, true)).isInstanceOf(AliasExpiredException.class);
        verify(repository, never()).incrementClickCount(anyString());
    }

    @Test
    @DisplayName("an unexpired alias can be resolved normally")
    void resolvesUnexpiredAlias() {
        Instant future = NOW.plusSeconds(3600);
        when(repository.findByAlias("active")).thenReturn(Optional.of(mapping("active", "https://spring.io", future)));

        String target = service.resolveAndTrack("active", null, true);

        assertThat(target).isEqualTo("https://spring.io");
        verify(repository).incrementClickCount("active");
    }

    @Test
    @DisplayName("details remain accessible for an expired alias")
    void returnsDetailsForExpiredAlias() {
        Instant past = NOW.minusSeconds(60);
        when(repository.findByAlias("old")).thenReturn(Optional.of(mapping("old", "https://spring.io", past)));

        ShortenUrlResponse response = service.getUrlDetails("old");

        assertThat(response.alias()).isEqualTo("old");
        assertThat(response.expiresAt()).isEqualTo(past);
    }

    @Test
    @DisplayName("details of an unknown alias are reported as not found")
    void rejectsDetailsForUnknownAlias() {
        when(repository.findByAlias("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUrlDetails("missing")).isInstanceOf(AliasNotFoundException.class);
    }

    @Test
    @DisplayName("statistics expose the click count and the short URL")
    void returnsStats() {
        when(repository.findByAlias("spring")).thenReturn(Optional.of(mapping("spring", "https://spring.io", null)));

        UrlStatsResponse stats = service.getStats("spring");

        assertThat(stats.alias()).isEqualTo("spring");
        assertThat(stats.shortUrl()).isEqualTo("http://localhost:8080/spring");
        assertThat(stats.clickCount()).isZero();
    }

    @Test
    @DisplayName("deleting an existing alias removes the mapping")
    void deletesExistingAlias() {
        UrlMapping mapping = mapping("spring", "https://spring.io", null);
        when(repository.findByAlias("spring")).thenReturn(Optional.of(mapping));

        service.deleteUrl("spring");

        verify(repository).delete(mapping);
    }

    @Test
    @DisplayName("deleting an unknown alias is reported as not found")
    void rejectsDeleteForUnknownAlias() {
        when(repository.findByAlias("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUrl("missing")).isInstanceOf(AliasNotFoundException.class);
        verify(repository, never()).delete(any());
    }
}

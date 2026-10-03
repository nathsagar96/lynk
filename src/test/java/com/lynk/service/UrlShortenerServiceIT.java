package com.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.lynk.AbstractIntegrationTestBase;
import com.lynk.domain.UrlMapping;
import com.lynk.dto.request.ShortenUrlRequest;
import com.lynk.dto.response.ShortenUrlResponse;
import com.lynk.dto.response.UrlStatsResponse;
import com.lynk.error.AliasAlreadyExistsException;
import com.lynk.error.InvalidUrlException;
import com.lynk.error.ReservedAliasException;
import com.lynk.error.UrlExpiredException;
import com.lynk.error.UrlNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("UrlShortenerService")
class UrlShortenerServiceIT extends AbstractIntegrationTestBase {

    private static final String DESTINATION = "https://example.com/landing";

    @Autowired
    private UrlShortenerService service;

    private ShortenUrlResponse shorten(String url) {
        return service.shorten(new ShortenUrlRequest(url, null, null));
    }

    private ShortenUrlResponse shortenWithAlias(String url, String alias) {
        return service.shorten(new ShortenUrlRequest(url, alias, null));
    }

    /**
     * Forces a mapping's expiry into the past so the 410 path can be exercised without waiting.
     */
    private void expireNow(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode).orElseThrow();
        mapping.setExpiresAt(Instant.now().minusSeconds(60));
        repository.saveAndFlush(mapping);
    }

    @Nested
    @DisplayName("shorten")
    class Shorten {

        @Test
        @DisplayName("returns an absolute short URL and a generated code")
        void shorten_returnsAbsoluteShortUrl_whenNoAliasGiven() {
            // Arrange & Act
            ShortenUrlResponse response = shorten("https://example.com/a/b?c=d");

            // Assert
            assertAll(
                    () -> assertThat(response.shortUrl()).startsWith("http://localhost:8080/"),
                    () -> assertThat(response.shortCode()).hasSize(7),
                    () -> assertThat(response.originalUrl()).isEqualTo("https://example.com/a/b?c=d"),
                    () -> assertThat(response.expiresAt()).isNull());
        }

        @Test
        @DisplayName("stamps a creation timestamp and a zero click count on persist")
        void shorten_stampsCreationTimestamp_whenPersisted() {
            // Arrange
            Instant before = Instant.now().minusSeconds(1);

            // Act
            ShortenUrlResponse response = shorten(DESTINATION);

            // Assert
            UrlMapping persisted =
                    repository.findByShortCode(response.shortCode()).orElseThrow();
            assertAll(
                    () -> assertThat(persisted.getCreatedAt()).isAfter(before),
                    () -> assertThat(persisted.getClickCount()).isZero());
        }

        @Test
        @DisplayName("generates a distinct code per request")
        void shorten_generatesDistinctCodes_acrossRequests() {
            // Arrange & Act
            String first = shorten("https://example.com/one").shortCode();
            String second = shorten("https://example.com/two").shortCode();

            // Assert
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("uses a custom alias verbatim")
        void shorten_usesCustomAliasVerbatim_whenAliasSupplied() {
            // Arrange & Act
            ShortenUrlResponse response = shortenWithAlias(DESTINATION, "my-alias");

            // Assert
            assertAll(
                    () -> assertThat(response.shortCode()).isEqualTo("my-alias"),
                    () -> assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/my-alias"));
        }

        @Test
        @DisplayName("rejects an alias that is already taken")
        void shorten_throwsAliasAlreadyExists_whenAliasIsTaken() {
            // Arrange
            shortenWithAlias("https://example.com/one", "dup-alias");

            // Act & Assert
            assertThatThrownBy(() -> shortenWithAlias("https://example.com/two", "dup-alias"))
                    .isInstanceOf(AliasAlreadyExistsException.class);
        }

        @ParameterizedTest(name = "alias \"{0}\" is rejected")
        @DisplayName("rejects a malformed alias")
        @ValueSource(strings = {"ab", "has space", "dot.dot", "café", "a+b"})
        void shorten_throwsInvalidUrl_whenAliasIsMalformed(String alias) {
            // Arrange & Act & Assert
            assertThatThrownBy(() -> shortenWithAlias(DESTINATION, alias)).isInstanceOf(InvalidUrlException.class);
        }

        @ParameterizedTest(name = "reserved alias \"{0}\" is rejected")
        @DisplayName("rejects a reserved alias")
        @ValueSource(strings = {"error", "api", "actuator", "health"})
        void shorten_throwsReservedAlias_whenAliasIsReserved(String alias) {
            // Arrange & Act & Assert
            assertThatThrownBy(() -> shortenWithAlias(DESTINATION, alias)).isInstanceOf(ReservedAliasException.class);
        }

        @Test
        @DisplayName("sets an expiry from the requested hours to live")
        void shorten_setsExpiry_whenHoursToExpireSupplied() {
            // Arrange
            Instant before = Instant.now().plus(23, ChronoUnit.HOURS);

            // Act
            ShortenUrlResponse response = service.shorten(new ShortenUrlRequest(DESTINATION, "ttl-alias", 24));

            // Assert
            assertThat(response.expiresAt()).isAfter(before);
        }

        @Test
        @DisplayName("normalises the scheme to lower case")
        void shorten_normalisesScheme_whenSchemeIsUpperCase() {
            // Arrange & Act
            ShortenUrlResponse response = shorten("HTTPS://example.com/x");

            // Assert
            assertThat(response.originalUrl()).isEqualTo("https://example.com/x");
        }

        @ParameterizedTest(name = "URL \"{0}\" is rejected")
        @DisplayName("rejects a URL that is not an absolute http(s) URL")
        @ValueSource(strings = {"   ", "/relative/path", "example.com", "ftp://example.com", "https://"})
        void shorten_throwsInvalidUrl_whenUrlIsUnusable(String url) {
            // Arrange & Act & Assert
            assertThatThrownBy(() -> shorten(url)).isInstanceOf(InvalidUrlException.class);
        }

        @Test
        @DisplayName("rejects a URL beyond the configured maximum length")
        void shorten_throwsInvalidUrl_whenUrlExceedsMaxLength() {
            // Arrange
            String overlong = "https://example.com/" + "a".repeat(2100);

            // Act & Assert
            assertThatThrownBy(() -> shorten(overlong))
                    .isInstanceOf(InvalidUrlException.class)
                    .hasMessageContaining("2048");
        }
    }

    @Nested
    @DisplayName("resolveAndCountClick")
    class ResolveAndCountClick {

        @Test
        @DisplayName("returns the destination and counts the click")
        void resolveAndCountClick_returnsDestinationAndCountsClick_whenCodeExists() {
            // Arrange
            shortenWithAlias(DESTINATION, "resolve-alias");

            // Act
            String resolved = service.resolveAndCountClick("resolve-alias");

            // Assert
            UrlMapping reloaded = repository.findByShortCode("resolve-alias").orElseThrow();
            assertAll(
                    () -> assertThat(resolved).isEqualTo(DESTINATION),
                    () -> assertThat(reloaded.getClickCount()).isEqualTo(1));
        }

        @Test
        @DisplayName("accumulates a count across repeated clicks")
        void resolveAndCountClick_accumulatesCount_whenCalledRepeatedly() {
            // Arrange
            shortenWithAlias(DESTINATION, "accumulate-alias");

            // Act
            service.resolveAndCountClick("accumulate-alias");
            service.resolveAndCountClick("accumulate-alias");
            service.resolveAndCountClick("accumulate-alias");

            // Assert
            UrlMapping reloaded = repository.findByShortCode("accumulate-alias").orElseThrow();
            assertThat(reloaded.getClickCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("throws for an unknown code")
        void resolveAndCountClick_throwsUrlNotFound_whenCodeIsUnknown() {
            // Arrange & Act & Assert
            assertThatThrownBy(() -> service.resolveAndCountClick("nosuchcode"))
                    .isInstanceOf(UrlNotFoundException.class);
        }

        @Test
        @DisplayName("throws for an expired code without counting the click")
        void resolveAndCountClick_throwsUrlExpired_whenCodeHasExpired() {
            // Arrange
            shortenWithAlias(DESTINATION, "expire-alias");
            expireNow("expire-alias");

            // Act
            assertThatThrownBy(() -> service.resolveAndCountClick("expire-alias"))
                    .isInstanceOf(UrlExpiredException.class);

            // Assert
            UrlMapping reloaded = repository.findByShortCode("expire-alias").orElseThrow();
            assertThat(reloaded.getClickCount()).isZero();
        }
    }

    @Nested
    @DisplayName("stats")
    class Stats {

        @Test
        @DisplayName("reflects the stored mapping and click count")
        void stats_reflectsStoredMapping_whenCodeExists() {
            // Arrange
            shortenWithAlias(DESTINATION, "stats-alias");
            service.resolveAndCountClick("stats-alias");

            // Act
            UrlStatsResponse stats = service.stats("stats-alias");

            // Assert
            assertAll(
                    () -> assertThat(stats.originalUrl()).isEqualTo(DESTINATION),
                    () -> assertThat(stats.shortUrl()).isEqualTo("http://localhost:8080/stats-alias"),
                    () -> assertThat(stats.clickCount()).isEqualTo(1),
                    () -> assertThat(stats.createdAt()).isNotNull());
        }

        @Test
        @DisplayName("remains available for a link that has expired")
        void stats_remainsAvailable_whenCodeHasExpired() {
            // Arrange
            shortenWithAlias(DESTINATION, "gone-alias");
            expireNow("gone-alias");

            // Act
            UrlStatsResponse stats = service.stats("gone-alias");

            // Assert
            assertAll(
                    () -> assertThat(stats.originalUrl()).isEqualTo(DESTINATION),
                    () -> assertThat(stats.expiresAt()).isBefore(Instant.now()));
        }

        @Test
        @DisplayName("throws for an unknown code")
        void stats_throwsUrlNotFound_whenCodeIsUnknown() {
            // Arrange & Act & Assert
            assertThatThrownBy(() -> service.stats("nosuchcode")).isInstanceOf(UrlNotFoundException.class);
        }
    }
}

package com.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.lynk.AbstractIntegrationTestBase;
import com.lynk.domain.UrlMapping;
import com.lynk.dto.request.ShortenUrlRequest;
import com.lynk.dto.response.ShortenUrlResponse;
import com.lynk.dto.response.UrlStatsResponse;
import com.lynk.error.LinkException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

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
    class Shorten {

        @Test
        void shorten_returnsAbsoluteShortUrl_whenNoAliasGiven() {
            ShortenUrlResponse response = shorten("https://example.com/a/b?c=d");

            assertAll(
                    () -> assertThat(response.shortUrl()).startsWith("http://localhost:8080/"),
                    () -> assertThat(response.shortCode()).hasSize(7),
                    () -> assertThat(response.originalUrl()).isEqualTo("https://example.com/a/b?c=d"),
                    () -> assertThat(response.expiresAt()).isNull());
        }

        @Test
        void shorten_stampsCreationTimestamp_whenPersisted() {
            Instant before = Instant.now().minusSeconds(1);

            ShortenUrlResponse response = shorten(DESTINATION);

            UrlMapping persisted =
                    repository.findByShortCode(response.shortCode()).orElseThrow();
            assertAll(
                    () -> assertThat(persisted.getCreatedAt()).isAfter(before),
                    () -> assertThat(persisted.getClickCount()).isZero());
        }

        @Test
        void shorten_generatesDistinctCodes_acrossRequests() {
            String first = shorten("https://example.com/one").shortCode();
            String second = shorten("https://example.com/two").shortCode();

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        void shorten_usesCustomAliasVerbatim_whenAliasSupplied() {
            ShortenUrlResponse response = shortenWithAlias(DESTINATION, "my-alias");

            assertAll(
                    () -> assertThat(response.shortCode()).isEqualTo("my-alias"),
                    () -> assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/my-alias"));
        }

        @Test
        void shorten_throwsAliasAlreadyExists_whenAliasIsTaken() {
            shortenWithAlias("https://example.com/one", "dup-alias");

            assertThatThrownBy(() -> shortenWithAlias("https://example.com/two", "dup-alias"))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/alias-conflict"));
        }

        @ParameterizedTest(name = "alias \"{0}\" is rejected")
        @ValueSource(strings = {"ab", "has space", "dot.dot", "café", "a+b"})
        void shorten_throwsInvalidUrl_whenAliasIsMalformed(String alias) {
            assertThatThrownBy(() -> shortenWithAlias(DESTINATION, alias))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/invalid-url"));
        }

        @ParameterizedTest(name = "reserved alias \"{0}\" is rejected")
        @ValueSource(strings = {"error", "api", "actuator", "health"})
        void shorten_throwsReservedAlias_whenAliasIsReserved(String alias) {
            assertThatThrownBy(() -> shortenWithAlias(DESTINATION, alias))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/reserved-alias"));
        }

        @Test
        void shorten_setsExpiry_whenHoursToExpireSupplied() {
            Instant before = Instant.now().plus(23, ChronoUnit.HOURS);

            ShortenUrlResponse response = service.shorten(new ShortenUrlRequest(DESTINATION, "ttl-alias", 24));

            assertThat(response.expiresAt()).isAfter(before);
        }

        @Test
        void shorten_normalisesScheme_whenSchemeIsUpperCase() {
            ShortenUrlResponse response = shorten("HTTPS://example.com/x");

            assertThat(response.originalUrl()).isEqualTo("https://example.com/x");
        }

        @ParameterizedTest(name = "URL \"{0}\" is rejected")
        @ValueSource(strings = {"   ", "/relative/path", "example.com", "ftp://example.com", "https://"})
        void shorten_throwsInvalidUrl_whenUrlIsUnusable(String url) {
            assertThatThrownBy(() -> shorten(url))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/invalid-url"));
        }

        @Test
        void shorten_throwsInvalidUrl_whenUrlExceedsMaxLength() {
            String overlong = "https://example.com/" + "a".repeat(2100);

            assertThatThrownBy(() -> shorten(overlong))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/invalid-url"))
                    .hasMessageContaining("2048");
        }
    }

    @Nested
    class ResolveAndCountClick {

        @Test
        void resolveAndCountClick_returnsDestinationAndCountsClick_whenCodeExists() {
            shortenWithAlias(DESTINATION, "resolve-alias");

            String resolved = service.resolveAndCountClick("resolve-alias");

            UrlMapping reloaded = repository.findByShortCode("resolve-alias").orElseThrow();
            assertAll(
                    () -> assertThat(resolved).isEqualTo(DESTINATION),
                    () -> assertThat(reloaded.getClickCount()).isEqualTo(1));
        }

        @Test
        void resolveAndCountClick_accumulatesCount_whenCalledRepeatedly() {
            shortenWithAlias(DESTINATION, "accumulate-alias");

            service.resolveAndCountClick("accumulate-alias");
            service.resolveAndCountClick("accumulate-alias");
            service.resolveAndCountClick("accumulate-alias");

            UrlMapping reloaded = repository.findByShortCode("accumulate-alias").orElseThrow();
            assertThat(reloaded.getClickCount()).isEqualTo(3);
        }

        @Test
        void resolveAndCountClick_throwsUrlNotFound_whenCodeIsUnknown() {
            assertThatThrownBy(() -> service.resolveAndCountClick("nosuchcode"))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/url-not-found"));
        }

        @Test
        void resolveAndCountClick_throwsUrlExpired_whenCodeHasExpired() {
            shortenWithAlias(DESTINATION, "expire-alias");
            expireNow("expire-alias");

            assertThatThrownBy(() -> service.resolveAndCountClick("expire-alias"))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/url-expired"));

            UrlMapping reloaded = repository.findByShortCode("expire-alias").orElseThrow();
            assertThat(reloaded.getClickCount()).isZero();
        }
    }

    @Nested
    class Stats {

        @Test
        void stats_reflectsStoredMapping_whenCodeExists() {
            shortenWithAlias(DESTINATION, "stats-alias");
            service.resolveAndCountClick("stats-alias");

            UrlStatsResponse stats = service.stats("stats-alias");

            assertAll(
                    () -> assertThat(stats.originalUrl()).isEqualTo(DESTINATION),
                    () -> assertThat(stats.shortUrl()).isEqualTo("http://localhost:8080/stats-alias"),
                    () -> assertThat(stats.clickCount()).isEqualTo(1),
                    () -> assertThat(stats.createdAt()).isNotNull());
        }

        @Test
        void stats_remainsAvailable_whenCodeHasExpired() {
            shortenWithAlias(DESTINATION, "gone-alias");
            expireNow("gone-alias");

            UrlStatsResponse stats = service.stats("gone-alias");

            assertAll(
                    () -> assertThat(stats.originalUrl()).isEqualTo(DESTINATION),
                    () -> assertThat(stats.expiresAt()).isBefore(Instant.now()));
        }

        @Test
        void stats_throwsUrlNotFound_whenCodeIsUnknown() {
            assertThatThrownBy(() -> service.stats("nosuchcode"))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/url-not-found"));
        }
    }
}

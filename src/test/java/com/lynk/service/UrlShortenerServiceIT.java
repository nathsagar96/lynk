package com.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.lynk.AbstractIntegrationTestBase;
import com.lynk.domain.UrlMapping;
import com.lynk.dto.request.ShortenUrlRequest;
import com.lynk.dto.response.LinkPageResponse;
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

    private static final String OWNER = "owner-subject";

    private static final String OTHER = "other-subject";

    @Autowired
    private UrlShortenerService service;

    private ShortenUrlResponse shorten(String url) {
        return service.shorten(new ShortenUrlRequest(url, null, null), OWNER);
    }

    private ShortenUrlResponse shortenWithAlias(String url, String alias) {
        return service.shorten(new ShortenUrlRequest(url, alias, null), OWNER);
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

            ShortenUrlResponse response = service.shorten(new ShortenUrlRequest(DESTINATION, "ttl-alias", 24), OWNER);

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

            UrlStatsResponse stats = service.stats("stats-alias", OWNER);

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

            UrlStatsResponse stats = service.stats("gone-alias", OWNER);

            assertAll(
                    () -> assertThat(stats.originalUrl()).isEqualTo(DESTINATION),
                    () -> assertThat(stats.expiresAt()).isBefore(Instant.now()));
        }

        @Test
        void stats_throwsUrlNotFound_whenCodeIsUnknown() {
            assertThatThrownBy(() -> service.stats("nosuchcode", OWNER))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/url-not-found"));
        }
    }

    @Nested
    class Ownership {

        @Test
        void shorten_attributesTheLinkToTheSubjectThatRegisteredIt() {
            service.shorten(new ShortenUrlRequest(DESTINATION, "owned-alias", null), OWNER);

            UrlMapping persisted = repository.findByShortCode("owned-alias").orElseThrow();

            assertThat(persisted.getOwner()).isEqualTo(OWNER);
        }

        @Test
        void stats_readsBack_whenTheOwnerAsks() {
            service.shorten(new ShortenUrlRequest(DESTINATION, "owner-reads", null), OWNER);

            UrlStatsResponse stats = service.stats("owner-reads", OWNER);

            assertThat(stats.originalUrl()).isEqualTo(DESTINATION);
        }

        @Test
        void stats_throwsUrlNotFound_whenAnotherSubjectAsks() {
            service.shorten(new ShortenUrlRequest(DESTINATION, "private-alias", null), OWNER);

            assertThatThrownBy(() -> service.stats("private-alias", OTHER))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/url-not-found"));
        }

        @Test
        void stats_throwsUrlNotFound_whenTheLinkPredatesOwnership() {
            // A row from before the owner column existed. It has no subject to match, so it reads as
            // missing rather than as readable by everyone.
            repository.saveAndFlush(new UrlMapping(DESTINATION, "legacy-alias", null, null));

            assertThatThrownBy(() -> service.stats("legacy-alias", OWNER))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/url-not-found"));
        }

        @Test
        void resolveAndCountClick_stillWorks_whenTheCallerIsNotTheOwner() {
            service.shorten(new ShortenUrlRequest(DESTINATION, "shared-alias", null), OWNER);

            assertThat(service.resolveAndCountClick("shared-alias")).isEqualTo(DESTINATION);
        }

        @Test
        void shorten_throwsAliasConflict_whenAnotherSubjectAlreadyTookTheAlias() {
            service.shorten(new ShortenUrlRequest(DESTINATION, "contested-alias", null), OWNER);

            assertThatThrownBy(
                            () -> service.shorten(new ShortenUrlRequest(DESTINATION, "contested-alias", null), OTHER))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/alias-conflict"));
        }
    }

    @Nested
    class Links {

        @Test
        void list_returnsOnlyTheOwnersLinks_newestFirst() {
            service.shorten(new ShortenUrlRequest(DESTINATION, "older-link", null), OWNER);
            service.shorten(new ShortenUrlRequest(DESTINATION, "newer-link", null), OWNER);
            service.shorten(new ShortenUrlRequest(DESTINATION, "their-link", null), OTHER);

            LinkPageResponse page = service.list(OWNER, 0, 20);

            assertAll(
                    () -> assertThat(page.totalElements()).isEqualTo(2),
                    () -> assertThat(page.content())
                            .extracting(UrlStatsResponse::shortUrl)
                            .containsExactly("http://localhost:8080/newer-link", "http://localhost:8080/older-link"));
        }

        @Test
        void list_splitsAcrossPages_whenSizeIsSmallerThanTheTotal() {
            service.shorten(new ShortenUrlRequest(DESTINATION, "page-one", null), OWNER);
            service.shorten(new ShortenUrlRequest(DESTINATION, "page-two", null), OWNER);
            service.shorten(new ShortenUrlRequest(DESTINATION, "page-three", null), OWNER);

            LinkPageResponse first = service.list(OWNER, 0, 2);
            LinkPageResponse second = service.list(OWNER, 1, 2);

            assertAll(
                    () -> assertThat(first.content()).hasSize(2),
                    () -> assertThat(first.totalElements()).isEqualTo(3),
                    () -> assertThat(second.content()).hasSize(1),
                    () -> assertThat(second.content().getFirst().shortUrl())
                            .isEqualTo("http://localhost:8080/page-one"));
        }

        @Test
        void list_isEmpty_whenTheOwnerHasNoLinks() {
            LinkPageResponse page = service.list("nobody-at-all", 0, 20);

            assertAll(
                    () -> assertThat(page.content()).isEmpty(),
                    () -> assertThat(page.totalElements()).isZero());
        }

        @Test
        void list_includesExpiredLinks() {
            service.shorten(new ShortenUrlRequest(DESTINATION, "expired-listed", null), OWNER);
            expireNow("expired-listed");

            LinkPageResponse page = service.list(OWNER, 0, 20);

            assertThat(page.content()).hasSize(1);
        }

        @Test
        void delete_removesTheOwnersLink() {
            service.shorten(new ShortenUrlRequest(DESTINATION, "doomed-link", null), OWNER);

            service.delete("doomed-link", OWNER);

            assertAll(
                    () -> assertThat(repository.findByShortCode("doomed-link")).isEmpty(),
                    () -> assertThat(service.list(OWNER, 0, 20).totalElements()).isZero(),
                    () -> assertThatThrownBy(() -> service.stats("doomed-link", OWNER))
                            .isInstanceOfSatisfying(
                                    LinkException.class,
                                    e -> assertThat(e.type()).isEqualTo("/problems/url-not-found")));
        }

        @Test
        void delete_throwsUrlNotFound_whenTheLinkBelongsToAnotherSubject() {
            service.shorten(new ShortenUrlRequest(DESTINATION, "not-yours", null), OWNER);

            assertThatThrownBy(() -> service.delete("not-yours", OTHER))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/url-not-found"));
        }

        @Test
        void delete_throwsUrlNotFound_whenTheCodeIsUnknown() {
            assertThatThrownBy(() -> service.delete("nosuchcode", OWNER))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/url-not-found"));
        }
    }
}

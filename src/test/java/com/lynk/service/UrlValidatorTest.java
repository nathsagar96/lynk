package com.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lynk.config.UrlShortenerProperties;
import com.lynk.error.LinkException;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class UrlValidatorTest {

    private static final Set<String> RESERVED = Set.of("error", "api", "actuator", "health");

    private final UrlValidator validator = new UrlValidator(
            new UrlShortenerProperties(new UrlShortenerProperties.Code(7, 5, 100), "http://localhost:8080", RESERVED));

    @Nested
    class ValidateUrl {

        @ParameterizedTest(name = "\"{0}\" is accepted")
        @ValueSource(
                strings = {
                    "http://example.com",
                    "https://example.com",
                    "https://example.com/a/b?c=d#e",
                    "https://sub.domain.example.com:8443/path",
                    "https://example.com/a%20b"
                })
        void validateUrl_acceptsAbsoluteHttpUrls_whenUrlIsWellFormed(String url) {
            assertThatCode(() -> validator.validateUrl(url)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "\"{0}\" is rejected")
        @ValueSource(
                strings = {
                    "",
                    "   ",
                    "/relative/path",
                    "example.com",
                    "ftp://example.com",
                    "javascript:alert(1)",
                    "file:///etc/passwd",
                    "https://",
                    "http://exa mple.com"
                })
        void validateUrl_rejectsUnusableUrls_whenUrlIsNotAbsoluteHttp(String url) {
            assertThatThrownBy(() -> validator.validateUrl(url))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/invalid-url"));
        }

        @Test
        void validateUrl_rejectsInvalidUrl_whenUrlIsNull() {
            assertThatThrownBy(() -> validator.validateUrl(null))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/invalid-url"));
        }

        @Test
        void validateUrl_rejectsInvalidUrl_whenUrlExceedsMaxLength() {
            String overlong = "https://example.com/" + "a".repeat(100);

            assertThatThrownBy(() -> validator.validateUrl(overlong))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/invalid-url"))
                    .hasMessageContaining("100");
        }

        @Test
        void validateUrl_normalisesScheme_whenSchemeIsUpperCase() {
            assertThat(validator.validateUrl("HTTPS://example.com/x")).isEqualTo("https://example.com/x");
        }
    }

    @Nested
    class ValidateAlias {

        @ParameterizedTest(name = "\"{0}\" is accepted")
        @ValueSource(strings = {"abc", "my-alias", "my_alias", "AbC123", "a-b_C9"})
        void validateAlias_acceptsWellFormedAliases_whenAliasMatchesPattern(String alias) {
            assertThat(validator.validateAlias(alias)).isEqualTo(alias);
        }

        @ParameterizedTest(name = "\"{0}\" is rejected")
        @ValueSource(strings = {"", "ab", "has space", "dot.dot", "slash/es", "café", "a+b"})
        void validateAlias_rejectsMalformedAliases_whenAliasDoesNotMatchPattern(String alias) {
            assertThatThrownBy(() -> validator.validateAlias(alias))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/invalid-url"));
        }

        @ParameterizedTest(name = "reserved alias \"{0}\" is rejected")
        @ValueSource(strings = {"error", "Error", "ERROR", "api", "actuator", "health"})
        void validateAlias_rejectsReservedAliases_whenAliasIsReserved(String alias) {
            assertThatThrownBy(() -> validator.validateAlias(alias))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/reserved-alias"));
        }

        @Test
        void validateAlias_acceptsWellFormedAlias_whenAliasIs32Characters() {
            String alias = "a".repeat(32);

            assertThat(validator.validateAlias(alias)).isEqualTo(alias);
        }

        @Test
        void validateAlias_rejectsMalformedAlias_whenAliasIs33Characters() {
            String alias = "a".repeat(33);

            assertThatThrownBy(() -> validator.validateAlias(alias))
                    .isInstanceOfSatisfying(
                            LinkException.class, e -> assertThat(e.type()).isEqualTo("/problems/invalid-url"));
        }

        @Test
        void validateAlias_trimsWhitespace_whenAliasIsPadded() {
            assertThat(validator.validateAlias("  spaced  ")).isEqualTo("spaced");
        }
    }
}

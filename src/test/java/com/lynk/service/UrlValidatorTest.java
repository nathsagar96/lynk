package com.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lynk.config.UrlShortenerProperties;
import com.lynk.error.InvalidUrlException;
import com.lynk.error.ReservedAliasException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@DisplayName("UrlValidator")
class UrlValidatorTest {

    private static final Set<String> RESERVED = Set.of("error", "api", "actuator", "health");

    private final UrlValidator validator = new UrlValidator(new UrlShortenerProperties(
            new UrlShortenerProperties.Code(7, 5, 100),
            new UrlShortenerProperties.Cleanup("0 0 1 * * ?"),
            "http://localhost:8080",
            RESERVED));

    @Nested
    @DisplayName("validateUrl")
    class ValidateUrl {

        @ParameterizedTest(name = "\"{0}\" is accepted")
        @DisplayName("accepts absolute http and https URLs")
        @ValueSource(
                strings = {
                    "http://example.com",
                    "https://example.com",
                    "https://example.com/a/b?c=d#e",
                    "https://sub.domain.example.com:8443/path",
                    "https://example.com/a%20b"
                })
        void validateUrl_acceptsAbsoluteHttpUrls_whenUrlIsWellFormed(String url) {
            // Arrange & Act & Assert
            assertThatCode(() -> validator.validateUrl(url)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "\"{0}\" is rejected")
        @DisplayName("rejects anything that is not an absolute http(s) URL")
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
            // Arrange & Act & Assert
            assertThatThrownBy(() -> validator.validateUrl(url)).isInstanceOf(InvalidUrlException.class);
        }

        @Test
        @DisplayName("rejects a null URL")
        void validateUrl_rejectsInvalidUrl_whenUrlIsNull() {
            // Arrange & Act & Assert
            assertThatThrownBy(() -> validator.validateUrl(null)).isInstanceOf(InvalidUrlException.class);
        }

        @Test
        @DisplayName("rejects a URL beyond the configured maximum length")
        void validateUrl_rejectsInvalidUrl_whenUrlExceedsMaxLength() {
            // Arrange
            String overlong = "https://example.com/" + "a".repeat(100);

            // Act & Assert
            assertThatThrownBy(() -> validator.validateUrl(overlong))
                    .isInstanceOf(InvalidUrlException.class)
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("lowercases the scheme so stored URLs are canonical")
        void validateUrl_normalisesScheme_whenSchemeIsUpperCase() {
            // Arrange & Act & Assert
            assertThat(validator.validateUrl("HTTPS://example.com/x")).isEqualTo("https://example.com/x");
        }
    }

    @Nested
    @DisplayName("validateAlias")
    class ValidateAlias {

        @ParameterizedTest(name = "\"{0}\" is accepted")
        @DisplayName("accepts well-formed aliases across the shared code alphabet")
        @ValueSource(strings = {"abc", "my-alias", "my_alias", "AbC123", "a-b_C9"})
        void validateAlias_acceptsWellFormedAliases_whenAliasMatchesPattern(String alias) {
            // Arrange & Act & Assert
            assertThat(validator.validateAlias(alias)).isEqualTo(alias);
        }

        @ParameterizedTest(name = "\"{0}\" is rejected")
        @DisplayName("rejects aliases outside 3-32 characters of the code alphabet")
        @ValueSource(strings = {"", "ab", "has space", "dot.dot", "slash/es", "café", "a+b"})
        void validateAlias_rejectsMalformedAliases_whenAliasDoesNotMatchPattern(String alias) {
            // Arrange & Act & Assert
            assertThatThrownBy(() -> validator.validateAlias(alias)).isInstanceOf(InvalidUrlException.class);
        }

        @ParameterizedTest(name = "reserved alias \"{0}\" is rejected")
        @DisplayName("rejects reserved aliases regardless of case")
        @ValueSource(strings = {"error", "Error", "ERROR", "api", "actuator", "health"})
        void validateAlias_rejectsReservedAliases_whenAliasIsReserved(String alias) {
            // Arrange & Act & Assert
            assertThatThrownBy(() -> validator.validateAlias(alias)).isInstanceOf(ReservedAliasException.class);
        }

        @Test
        @DisplayName("accepts an alias at the 32-character upper bound")
        void validateAlias_acceptsWellFormedAlias_whenAliasIs32Characters() {
            // Arrange
            String alias = "a".repeat(32);

            // Act & Assert
            assertThat(validator.validateAlias(alias)).isEqualTo(alias);
        }

        @Test
        @DisplayName("rejects an alias one character over the upper bound")
        void validateAlias_rejectsMalformedAlias_whenAliasIs33Characters() {
            // Arrange
            String alias = "a".repeat(33);

            // Act & Assert
            assertThatThrownBy(() -> validator.validateAlias(alias)).isInstanceOf(InvalidUrlException.class);
        }

        @Test
        @DisplayName("trims surrounding whitespace")
        void validateAlias_trimsWhitespace_whenAliasIsPadded() {
            // Arrange & Act & Assert
            assertThat(validator.validateAlias("  spaced  ")).isEqualTo("spaced");
        }
    }
}

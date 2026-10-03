package com.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lynk.config.UrlShortenerProperties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag("unit")
@DisplayName("ShortCodeGenerator")
class ShortCodeGeneratorTest {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private ShortCodeGenerator generatorWith(int length) {
        UrlShortenerProperties properties = new UrlShortenerProperties(
                new UrlShortenerProperties.Code(length, 5, 2048),
                new UrlShortenerProperties.Cleanup("0 0 1 * * ?"),
                "http://localhost:8080",
                Set.of("error"));
        return new ShortCodeGenerator(properties);
    }

    @Nested
    @DisplayName("generate")
    class Generate {

        @ParameterizedTest(name = "length {0} produces a code of {0} characters")
        @DisplayName("returns a code of the configured length")
        @CsvSource({"1", "3", "7", "12", "32"})
        void generate_returnsCodeOfConfiguredLength_whenLengthIsConfigured(int length) {
            // Arrange
            ShortCodeGenerator generator = generatorWith(length);

            // Act
            String code = generator.generate();

            // Assert
            assertThat(code).hasSize(length);
        }

        @Test
        @DisplayName("uses only base-62 characters across many draws")
        void generate_usesOnlyBase62Characters_acrossManyDraws() {
            // Arrange
            ShortCodeGenerator generator = generatorWith(12);

            // Act
            Set<String> codesWithIllegalCharacters = IntStream.range(0, 500)
                    .mapToObj(i -> generator.generate())
                    .filter(code -> code.chars().anyMatch(c -> ALPHABET.indexOf(c) < 0))
                    .collect(Collectors.toSet());

            // Assert
            assertThat(codesWithIllegalCharacters).isEmpty();
        }

        @Test
        @DisplayName("produces distinct codes")
        void generate_producesDistinctCodes_acrossManyDraws() {
            // Arrange
            ShortCodeGenerator generator = generatorWith(7);

            // Act
            Set<String> codes =
                    IntStream.range(0, 1000).mapToObj(i -> generator.generate()).collect(Collectors.toSet());

            // Assert
            assertThat(codes).hasSize(1000);
        }

        @ParameterizedTest(name = "length {0} is rejected")
        @DisplayName("rejects a non-positive configured length")
        @CsvSource({"0", "-1"})
        void generate_throws_whenConfiguredLengthIsNotPositive(int length) {
            // Arrange
            ShortCodeGenerator generator = generatorWith(length);

            // Act & Assert
            assertThatThrownBy(generator::generate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("greater than zero");
        }
    }
}

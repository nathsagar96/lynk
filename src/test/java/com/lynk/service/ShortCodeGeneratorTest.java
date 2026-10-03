package com.lynk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lynk.config.UrlShortenerProperties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag("unit")
class ShortCodeGeneratorTest {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private ShortCodeGenerator generatorWith(int length) {
        UrlShortenerProperties properties = new UrlShortenerProperties(
                new UrlShortenerProperties.Code(length, 5, 2048), "http://localhost:8080", Set.of("error"));
        return new ShortCodeGenerator(properties);
    }

    @Nested
    class Generate {

        @ParameterizedTest(name = "length {0} produces a code of {0} characters")
        @CsvSource({"1", "3", "7", "12", "32"})
        void generate_returnsCodeOfConfiguredLength_whenLengthIsConfigured(int length) {
            ShortCodeGenerator generator = generatorWith(length);

            String code = generator.generate();

            assertThat(code).hasSize(length);
        }

        @Test
        void generate_usesOnlyBase62Characters_acrossManyDraws() {
            ShortCodeGenerator generator = generatorWith(12);

            Set<String> codesWithIllegalCharacters = IntStream.range(0, 500)
                    .mapToObj(i -> generator.generate())
                    .filter(code -> code.chars().anyMatch(c -> ALPHABET.indexOf(c) < 0))
                    .collect(Collectors.toSet());

            assertThat(codesWithIllegalCharacters).isEmpty();
        }

        @Test
        void generate_producesDistinctCodes_acrossManyDraws() {
            ShortCodeGenerator generator = generatorWith(7);

            Set<String> codes =
                    IntStream.range(0, 1000).mapToObj(i -> generator.generate()).collect(Collectors.toSet());

            assertThat(codes).hasSize(1000);
        }

        @ParameterizedTest(name = "length {0} is rejected")
        @CsvSource({"0", "-1"})
        void generate_throws_whenConfiguredLengthIsNotPositive(int length) {
            ShortCodeGenerator generator = generatorWith(length);

            assertThatThrownBy(generator::generate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("greater than zero");
        }
    }
}

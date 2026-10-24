package ly.lynk.service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/** Generates random Base62 ({@code [0-9a-zA-Z]}) aliases. */
@Component
public class AliasGenerator {

    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private final RandomGenerator random;

    /**
     * Creates a thread-safe alias generator backed by {@link ThreadLocalRandom}.
     */
    public AliasGenerator() {
        this(null);
    }

    /**
     * Creates an alias generator with the given random number generator.
     *
     * @param random the random number generator, or {@code null} to use {@link ThreadLocalRandom}
     */
    AliasGenerator(RandomGenerator random) {
        this.random = random;
    }

    /**
     * Generates a random Base62 alias of the specified length.
     *
     * @param length the desired character length, must be positive
     * @return the generated Base62 alias string
     * @throws IllegalArgumentException if length is non-positive
     */
    public String generate(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Alias length must be positive");
        }
        StringBuilder builder = new StringBuilder(length);
        RandomGenerator rng = random != null ? random : ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET[rng.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}

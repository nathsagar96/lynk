package com.lynk.service;

import com.lynk.config.UrlShortenerProperties;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Produces random base-62 short codes, keeping row ids unguessable and out of the public API.
 */
@Component
@RequiredArgsConstructor
public class ShortCodeGenerator {

    /**
     * Digits, lowercase and uppercase letters: 62 symbols, no collation surprises.
     */
    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private final SecureRandom random = new SecureRandom();

    private final UrlShortenerProperties properties;

    /**
     * Generates a code of the configured length.
     *
     * @throws IllegalStateException if the configured length is not positive
     */
    public String generate() {
        int length = properties.code().length();
        if (length <= 0) {
            throw new IllegalStateException("lynk.code.length must be greater than zero");
        }
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}

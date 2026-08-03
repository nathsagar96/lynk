package ly.lynk.shortcode;

public final class Base62Encoder {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();
    private static final int ENCODED_LENGTH = 11;

    private Base62Encoder() {}

    public static String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID must be non-negative: " + id);
        }
        char[] chars = new char[ENCODED_LENGTH];
        long remaining = id;
        for (int i = ENCODED_LENGTH - 1; i >= 0; i--) {
            chars[i] = ALPHABET.charAt((int) (remaining % BASE));
            remaining /= BASE;
        }
        return new String(chars);
    }
}

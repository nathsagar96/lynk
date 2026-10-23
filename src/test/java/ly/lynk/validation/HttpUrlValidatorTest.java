package ly.lynk.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HttpUrlValidatorTest {

    private static final HttpUrlValidator VALIDATOR = new HttpUrlValidator();

    @ParameterizedTest
    @CsvSource({
        "http://example.com, true",
        "https://example.com, true",
        "HTTP://EXAMPLE.COM, true",
        "hTtP://eXaMpLe.CoM:8080/path?q=1, true",
        "http://localhost, true",
        "http://machine, true",
        "http://example.com:8080/path?x=1#frag, true",
        "https://example.com/u/john@doe, true",
        "https://forms.example.com/x?email=user@example.com, true",
        "https://mastodon.example/@springboot, true",
        "http://user:pass@example.com, false",
        "http://user@example.com, false",
        "http://127.0.0.1, false",
        "http://10.0.0.5/x, false",
        "http://10.0.0.5/x:1, false",
        "http://169.254.169.254/latest/meta-data, false",
        "http://[::1], false",
        "http://[2001:db8::1], false",
        "ftp://example.com, false",
        "mailto:admin@example.com, false",
        "not a url, false",
        "http://, false",
    })
    void acceptsOrRejects(String input, boolean expectedAccepted) {
        assertThat(VALIDATOR.isValid(input, null)).isEqualTo(expectedAccepted);
    }
}

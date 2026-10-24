package ly.lynk.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * Accepts only absolute http(s) URLs without credentials in them and without an IP-literal host.
 *
 * <p>Structural parsing is delegated to {@link URI}; only the policy checks are handwritten.
 */
public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    /**
     * Validates whether the given string is a valid absolute HTTP or HTTPS URL.
     *
     * @param value the URL string to validate
     * @param context the constraint validator context
     * @return {@code true} if value is null or a valid HTTP(S) URL, {@code false} otherwise
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = uri.getHost();
        return host != null
                && uri.getUserInfo() == null
                && !host.startsWith("[")
                && !IPV4_LITERAL.matcher(host).matches();
    }
}

package ly.lynk;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Test configuration supplying a mocked {@link JwtDecoder} for offline, hermetic testing. */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

    @Bean
    JwtDecoder jwtDecoder() {
        return Mockito.mock(JwtDecoder.class);
    }
}

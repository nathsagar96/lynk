package ly.lynk;

import ly.lynk.config.AppProperties;
import org.mockito.Mockito;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Test configuration supplying a mocked {@link JwtDecoder} for offline, hermetic testing.
 *
 * <p>Application properties are registered explicitly because {@code @WebMvcTest} slices import
 * {@code SecurityConfig} without going through {@code @ConfigurationPropertiesScan} on the application class.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
public class TestSecurityConfiguration {

    @Bean
    JwtDecoder jwtDecoder() {
        return Mockito.mock(JwtDecoder.class);
    }
}

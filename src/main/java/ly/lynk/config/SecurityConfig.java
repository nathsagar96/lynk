package ly.lynk.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Configures HTTP security, OAuth2 resource server JWT validation, and CORS policies. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppProperties properties;

    /**
     * Constructs security configuration with application properties.
     *
     * @param properties configured application properties
     */
    public SecurityConfig(AppProperties properties) {
        this.properties = properties;
    }

    /**
     * Builds the main security filter chain.
     *
     * @param http the HTTP security builder
     * @return the configured security filter chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/{alias:[a-zA-Z0-9_-]+}")
                        .permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/{alias:[a-zA-Z0-9_-]+}")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/urls/{alias}")
                        .permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/urls/{alias}")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/urls/{alias}/qr")
                        .permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/urls/{alias}/qr")
                        .permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Configures development CORS allowing standard localhost frontend origins.
     *
     * @return the CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(properties.corsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(HttpHeaders.LOCATION));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

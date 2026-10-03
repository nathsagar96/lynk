package com.lynk.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.lynk.error.ProblemAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secures the API with Keycloak-issued bearer tokens.
 * <p>
 * The service is a resource server, not a client: it never sees a password and never starts a
 * login. Keycloak mints the token, this filter chain only validates it against the realm's
 * published keys ({@code spring.security.oauth2.resourceserver.jwt.issuer-uri}).
 * <p>
 * Every route is public by default except {@code /api/**}, which needs a valid token. That default
 * is deliberate: {@code GET /{shortCode}} is the redirect strangers follow and must stay open, and
 * leaving unmatched paths permitted keeps a bad path a 404 rather than turning it into a 403.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class SecurityConfig {

    private final ProblemAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless bearer-token API: no cookie and no session, so there is no CSRF vector
                // and CSRF protection would only reject every POST.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Both write the / read-the-API operations live here; the redirect route, the
                        // health endpoint and the OpenAPI document fall through to permitAll().
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
                // The filter chain answers before MVC, so a 401 never reaches ApiExceptionHandler and
                // has to be shaped into the same problem document here.
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint));
        return http.build();
    }
}

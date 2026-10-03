package com.lynk.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the API for springdoc, which serves it at {@code /v3/api-docs} and renders it with
 * Swagger UI at {@code /swagger-ui.html}. The redirect route is {@code @Hidden} — see
 * {@code RedirectController}.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI lynkOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lynk URL shortener")
                        .description("""
                        Shorten a long URL to a short code, follow it, and read its click count.

                        Every error is an RFC 9457 problem document served as `application/problem+json`.
                        `type` is the stable machine-readable handle and `status` is the HTTP status;
                        `title` and `detail` are prose and may be reworded, so branch on `type` and
                        `status` and never on `detail`.

                        The API is protected by a Keycloak-issued bearer token; send it as
                        `Authorization: Bearer <token>`. The redirect route is the only thing you can
                        call without one.""")
                        // The /api/v1 contract version, not the artifact version in pom.xml.
                        .version("1.0.0")
                        .license(new License().name("MIT")))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("A Keycloak access token for this realm.")));
    }
}

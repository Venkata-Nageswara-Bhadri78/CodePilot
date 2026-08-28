package com.developer.copilot.common.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.developer.copilot.common.dto.ApiResponse;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {

    /** Shared example shown for every documented error response, kept in one place so it
     * stays consistent with the real {@link ApiResponse} shape returned by
     * {@code GlobalExceptionHandler}. */
    private static final String ERROR_RESPONSE_EXAMPLE =
            "{\"success\":false,\"message\":\"A human-readable description of what went wrong.\","
                    + "\"data\":null,\"timestamp\":\"2026-01-01T12:00:00\"}";

    @Bean
    public OpenAPI openAPI() {

        final String securitySchemeName = "Bearer Authentication";

        return new OpenAPI()

                .info(new Info()
                        .title("Copilot REST API")
                        .description("Backend APIs for Copilot - a career assistant platform. "
                                + "All endpoints (except auth) require a Bearer JWT obtained from /api/v1/auth/login. "
                                + "Every response, success or error, is wrapped in the shared 'ApiResponse' schema.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Bhadri Venkata Nageswara")
                                .email("support@copilot.dev"))
                        .license(new License()
                                .name("Apache 2.0")))

                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(securitySchemeName))

                .schemaRequirement(
                        securitySchemeName,

                        new SecurityScheme()

                                .name(securitySchemeName)

                                .type(SecurityScheme.Type.HTTP)

                                .scheme("bearer")

                                .bearerFormat("JWT"))

                .components(sharedErrorResponseComponents());

    }

    /**
     * Registers one reusable "error" response definition, backed by the real
     * {@link ApiResponse} shape, so every documented error response across the app is
     * consistent instead of an empty/generic schema per endpoint.
     */
    private Components sharedErrorResponseComponents() {
        return new Components()
                .addResponses("ApiErrorResponse",
                        new io.swagger.v3.oas.models.responses.ApiResponse()
                                .description("Standard error envelope returned for validation, "
                                        + "authentication, conflict, and unexpected-failure cases.")
                                .content(new Content().addMediaType(
                                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                                        new MediaType()
                                                .addExamples("default",
                                                        new Example().value(ERROR_RESPONSE_EXAMPLE)))));
    }

    /**
     * Groups public, client-facing endpoints separately from {@code /api/v1/internal/**}
     * (documented in its own group by the owning service) so the Swagger UI dropdown clearly
     * separates "what a frontend developer should call" from service-to-service-only routes.
     */
    @Bean
    public GroupedOpenApi publicOpenApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName("Public API")
                .pathsToMatch("/api/v1/**")
                .pathsToExclude("/api/v1/internal/**")
                .build();
    }

}
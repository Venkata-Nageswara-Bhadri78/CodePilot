package com.developer.copilot.common.config;

import java.util.List;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.developer.copilot.common.dto.ApiResponse;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
@Profile("!prod & !production")
public class SwaggerConfig {

    static final String BEARER_SCHEME = "Bearer Authentication";
    static final String INTERNAL_API_KEY_SCHEME = "InternalApiKey";

    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-otp",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/refresh-token");

    /** Shared example shown for every documented error response, kept in one place so it
     * stays consistent with the real {@link ApiResponse} shape returned by
     * {@code GlobalExceptionHandler}. */
    private static final String ERROR_RESPONSE_EXAMPLE =
            "{\"success\":false,\"message\":\"A human-readable description of what went wrong.\","
                    + "\"data\":null,\"timestamp\":\"2026-01-01T12:00:00\"}";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Copilot REST API")
                        .description("""
                                One Spring Boot app — pick a group (Authentication, Users, Jobs, Job extraction, AI, Chat assistant, Internal Service API). There is no Common group.
                                Login in Authentication, then Authorize with the access JWT. The user must be enabled and email-verified or every other call is 401 "Unauthorized."
                                /api/v1/internal/** is not for the SPA: JWT plus X-Internal-Api-Key. Ownership is still the JWT user.
                                Every JSON error is ApiResponse with success false. Filters may return that shape without going through a controller (internal key 401 is "Invalid or missing internal service key."; missing JWT is "Unauthorized."; CurrentUserService is "User is not authenticated.").
                                Production disables this UI. If you can see Swagger on the public hostname, the Spring profile is wrong.""")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Bhadri Venkata Nageswara")
                                .email("support@copilot.dev")))
                .addServersItem(new Server().url("http://localhost:8080").description("Local"))
                .addServersItem(new Server().url("https://api.yourdomain.com").description("Production host (docs only)"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(sharedComponents());
    }

    /**
     * One Components object so Bearer / InternalApiKey schemes are not wiped by replacing
     * components after {@code schemaRequirement}.
     */
    private Components sharedComponents() {
        return new Components()
                .addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access JWT from POST /api/v1/auth/login. Required on every non-public URL."))
                .addSecuritySchemes(INTERNAL_API_KEY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Api-Key")
                                .description("Service-to-service key for /api/v1/internal/**. Never in the SPA. Does not replace the JWT."))
                .addSchemas("ApiResponse",
                        new ObjectSchema()
                                .description("Uniform response envelope. timestamp is LocalDateTime without a timezone.")
                                .addProperty("success", new BooleanSchema())
                                .addProperty("message", new StringSchema())
                                .addProperty("data", new ObjectSchema().nullable(true)
                                        .description("Payload; null on errors and on void successes."))
                                .addProperty("timestamp", new StringSchema().format("date-time")
                                        .description("Server LocalDateTime, no offset. Do not assume UTC unless the host is UTC.")))
                .addResponses("ApiErrorResponse",
                        new io.swagger.v3.oas.models.responses.ApiResponse()
                                .description("Standard error envelope returned for validation, "
                                        + "authentication, conflict, and unexpected-failure cases. "
                                        + "Statuses this app actually emits include 400, 401, 403, 404, 405, "
                                        + "409, 415, 422, 429 (Retry-After on most limiters), 500, 502, 503.")
                                .content(new Content().addMediaType(
                                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                                        new MediaType()
                                                .addExamples("default",
                                                        new Example().value(ERROR_RESPONSE_EXAMPLE)))));
    }

    /**
     * Public auth URLs are permitAll. Clear the global Bearer requirement so Try-it-out
     * does not imply login needs a JWT.
     */
    @Bean
    public OpenApiCustomizer publicAuthPathsAnonymous() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                if (item != null && PUBLIC_AUTH_PATHS.contains(path)) {
                    item.readOperations().forEach(op -> op.setSecurity(List.of()));
                }
            });
        };
    }
}

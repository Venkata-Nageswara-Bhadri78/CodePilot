package com.developer.copilot.user.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalOpenApiConfig {

    private static final String INTERNAL_API_KEY = "InternalApiKey";

    @Bean
    public GroupedOpenApi internalOpenApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .displayName("Internal Service API")
                .pathsToMatch("/api/v1/internal/**")
                .addOpenApiCustomizer(openApi ->
                        openApi.getComponents()
                                .addSecuritySchemes(
                                        INTERNAL_API_KEY,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.HEADER)
                                                .name("X-Internal-Api-Key")
                                                .description("Internal service-to-service API key")
                                ))
                .build();
    }
}
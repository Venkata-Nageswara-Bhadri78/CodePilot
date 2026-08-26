package com.developer.copilot.user.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps internal service-to-service endpoints in their own Swagger group so they
 * are never mistaken for public user-facing API.
 */
@Configuration
public class InternalOpenApiConfig {

    @Bean
    public GroupedOpenApi internalOpenApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .displayName("Internal Service API")
                .pathsToMatch("/api/v1/internal/**")
                .build();
    }
}

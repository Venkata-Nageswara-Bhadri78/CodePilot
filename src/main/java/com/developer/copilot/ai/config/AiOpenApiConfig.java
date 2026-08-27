package com.developer.copilot.ai.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Isolates AI career-copilot endpoints into their own Swagger UI group.
 */
@Configuration
public class AiOpenApiConfig {

    @Bean
    public GroupedOpenApi aiOpenApi() {
        return GroupedOpenApi.builder()
                .group("ai")
                .displayName("AI Service")
                .pathsToMatch("/api/v1/ai/**")
                .build();
    }
}

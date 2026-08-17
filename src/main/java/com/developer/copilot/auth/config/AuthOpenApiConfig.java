package com.developer.copilot.auth.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthOpenApiConfig {

    @Bean
    public GroupedOpenApi authOpenApi() {
        return GroupedOpenApi.builder()
                .group("authentication")
                .displayName("Authentication API")
                .pathsToMatch("/api/v1/auth/**")
                .build();
    }
}


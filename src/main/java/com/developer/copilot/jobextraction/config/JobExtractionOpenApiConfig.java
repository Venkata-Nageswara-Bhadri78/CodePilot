package com.developer.copilot.jobextraction.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Isolates the Job Content Extraction endpoint into its own Swagger UI group, matching the
 * pattern already used for the {@code auth} and {@code jobs} modules, so frontend/QA can
 * find and try it without scrolling through every other module's endpoints.
 */
@Configuration
public class JobExtractionOpenApiConfig {

    @Bean
    public GroupedOpenApi jobExtractionOpenApi() {
        return GroupedOpenApi.builder()
                .group("job-extraction")
                .displayName("Job Content Extraction")
                .pathsToMatch("/api/v1/job-extraction/**")
                .build();
    }
}

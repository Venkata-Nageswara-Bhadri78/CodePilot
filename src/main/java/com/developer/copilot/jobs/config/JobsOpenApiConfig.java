package com.developer.copilot.jobs.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobsOpenApiConfig {

    @Bean
    public GroupedOpenApi jobsOpenApi() {
        return GroupedOpenApi.builder()
                .group("jobs")
                .displayName("Jobs API")
                .pathsToMatch("/api/v1/jobs/**")
                .build();
    }
}

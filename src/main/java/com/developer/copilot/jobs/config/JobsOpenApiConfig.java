package com.developer.copilot.jobs.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

@Configuration
@Profile("!prod & !production")
public class JobsOpenApiConfig {

    @Bean
    public GroupedOpenApi jobsOpenApi() {
        return GroupedOpenApi.builder()
                .group("jobs")
                .displayName("Jobs API")
                .pathsToMatch("/api/v1/jobs/**")
                .addOpenApiCustomizer(JobsOpenApiConfig::customize)
                .build();
    }

    private static void customize(OpenAPI openApi) {
        openApi.addServersItem(new Server().url("http://localhost:8080").description("Local"));
        openApi.addServersItem(new Server().url("https://api.yourdomain.com").description("Production host (docs only)"));
        if (openApi.getInfo() == null) {
            openApi.info(new Info().title("Jobs API"));
        }
        openApi.addTagsItem(new Tag()
                .name("Jobs")
                .description("Personal job-posting notebook for the signed-in user. "
                        + "Obtain a JWT from POST /api/v1/auth/login (Authentication group), then Authorize. "
                        + "PUT is a full replace: mandatory fields are required again and omitting skills clears them. "
                        + "PATCH sends only dirty fields; PATCH /{id}/skills accepts [] to clear. "
                        + "Optional field routes accept empty string to clear. "
                        + "Foreign or missing ids return 404. Duplicate source URLs return 409 "
                        + "\"This post was already added to your records.\" "
                        + "List search is a contains-match on title, company, location, industry, and sourcePlatform. "
                        + "Page size max is 50. salary is not a sort field."));
    }
}

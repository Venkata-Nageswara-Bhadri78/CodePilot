package com.developer.copilot.community.config;

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
public class CommunityOpenApiConfig {

    @Bean
    public GroupedOpenApi communityOpenApi() {
        return GroupedOpenApi.builder()
                .group("community")
                .displayName("Community API")
                .pathsToMatch("/api/v1/community/**")
                .addOpenApiCustomizer(CommunityOpenApiConfig::customize)
                .build();
    }

    private static void customize(OpenAPI openApi) {
        openApi.addServersItem(new Server().url("http://localhost:8080").description("Local"));
        if (openApi.getInfo() == null) {
            openApi.info(new Info().title("Community API"));
        }
        openApi.addTagsItem(new Tag()
                .name("Community account")
                .description("Opt-in community account. GET /api/v1/community/account reads consent and does not create a row. "
                        + "POST accepts the current app.community.terms-version. "
                        + "accepted must be true. A mismatched version is 400. "
                        + "The same version again returns the existing account. "
                        + "A changed configured version requires a new acceptance on the same row. "
                        + "The owner is the JWT user. Unknown JSON fields, including a user id, are rejected."));
        openApi.addTagsItem(new Tag()
                .name("Community")
                .description("Job-posting communities. GET /api/v1/community/jobs/{jobId}/prefill reads the caller's saved job. "
                        + "POST /api/v1/community/resolve checks URLs and does not join. "
                        + "POST /api/v1/community creates a community, its aliases, and the creator membership, "
                        + "or returns the existing community without joining. "
                        + "POST /api/v1/community/descriptions/refine rewrites description text only."));
    }
}

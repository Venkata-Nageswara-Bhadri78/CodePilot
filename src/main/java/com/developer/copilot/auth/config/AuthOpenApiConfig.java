package com.developer.copilot.auth.config;

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
public class AuthOpenApiConfig {

    @Bean
    public GroupedOpenApi authOpenApi() {
        return GroupedOpenApi.builder()
                .group("authentication")
                .displayName("Authentication API")
                .pathsToMatch("/api/v1/auth/**")
                .addOpenApiCustomizer(AuthOpenApiConfig::customize)
                .build();
    }

    private static void customize(OpenAPI openApi) {
        openApi.addServersItem(new Server().url("http://localhost:8080").description("Local"));
        openApi.addServersItem(new Server().url("https://api.yourdomain.com").description("Production host (docs only)"));
        if (openApi.getInfo() == null) {
            openApi.info(new Info().title("Authentication API"));
        }
        openApi.addTagsItem(new Tag()
                .name("Authentication")
                .description("Use this group for identity. Register → verify-email → login, then Authorize with the access JWT. "
                        + "Refresh-token is public and takes the UUID from login, not the JWT."));
    }
}

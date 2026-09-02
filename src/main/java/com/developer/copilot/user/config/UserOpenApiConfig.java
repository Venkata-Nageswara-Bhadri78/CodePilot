package com.developer.copilot.user.config;

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
public class UserOpenApiConfig {

    @Bean
    public GroupedOpenApi userOpenApi() {
        return GroupedOpenApi.builder()
                .group("user")
                .displayName("User API")
                .pathsToMatch("/api/v1/users/**")
                .addOpenApiCustomizer(UserOpenApiConfig::customize)
                .build();
    }

    private static void customize(OpenAPI openApi) {
        openApi.addServersItem(new Server().url("http://localhost:8080").description("Local"));
        openApi.addServersItem(new Server().url("https://api.yourdomain.com").description("Production host (docs only)"));
        if (openApi.getInfo() == null) {
            openApi.info(new Info().title("User API"));
        }
        openApi.addTagsItem(new Tag()
                .name("User - Profile")
                .description("Frontend career folder. 1) Authorize with JWT from POST /api/v1/auth/login. "
                        + "2) POST /api/v1/users/profile once. 3) Add children as needed "
                        + "(max 20 per collection). 4) POST /api/v1/users/resumes (multipart field `file`, PDF, max 5MB). "
                        + "First resume is high-priority. PUT profile replaces headline/summary/technicalSkills "
                        + "including JSON null (omitted fields become null). "
                        + "Do not call /api/v1/internal/** from the SPA. "
                        + "Upload returns 201. Download is a raw PDF, not ApiResponse. "
                        + "Parse status is not on the public resume list."));
        openApi.addTagsItem(new Tag()
                .name("User - Resumes")
                .description("PDF store for the current user. JWT required (enabled + email verified). "
                        + "Duplicate SHA-256 among this profile's resumes is 409. "
                        + "10 active resumes max. 429 + Retry-After if upload budget is spent."));
    }
}

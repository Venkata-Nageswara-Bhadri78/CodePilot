package com.developer.copilot.chatassistant.config;

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
public class CustomPromptOpenApiConfig {

    @Bean
    public GroupedOpenApi customPromptOpenApi() {
        return GroupedOpenApi.builder()
                .group("custom-prompts")
                .displayName("Custom Prompts")
                .pathsToMatch("/api/v1/custom-prompts/**")
                .addOpenApiCustomizer(CustomPromptOpenApiConfig::customize)
                .build();
    }

    private static void customize(OpenAPI openApi) {
        openApi.addServersItem(new Server().url("http://localhost:8080").description("Local"));
        openApi.addServersItem(new Server().url("https://api.yourdomain.com").description("Production host (docs only)"));
        if (openApi.getInfo() == null) {
            openApi.info(new Info().title("Custom Prompts API"));
        }
        openApi.addTagsItem(new Tag()
                .name("Custom Prompts")
                .description("Reusable prompts for the signed-in user. Authorize with JWT from POST /api/v1/auth/login. "
                        + "POST creates a prompt. GET lists all of the caller's prompts (newest-updated first, not paged). "
                        + "GET/PUT/DELETE /{id} operate on one owned row. Title max 255, prompt max 8000. "
                        + "Titles are not unique. Foreign or missing ids return 404 with the same message. "
                        + "This is not job chat and does not call Gemini. Browser-extension tokens cannot call these routes."));
    }
}

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
public class ChatAssistantOpenApiConfig {

    @Bean
    public GroupedOpenApi chatAssistantOpenApi() {
        return GroupedOpenApi.builder()
                .group("chat-assistant")
                .displayName("Chat Assistant")
                .pathsToMatch("/api/v1/chat-assistant/**")
                .addOpenApiCustomizer(ChatAssistantOpenApiConfig::customize)
                .build();
    }

    private static void customize(OpenAPI openApi) {
        openApi.addServersItem(new Server().url("http://localhost:8080").description("Local"));
        openApi.addServersItem(new Server().url("https://api.yourdomain.com").description("Production host (docs only)"));
        if (openApi.getInfo() == null) {
            openApi.info(new Info().title("Chat Assistant API"));
        }
        openApi.addTagsItem(new Tag()
                .name("Chat Assistant")
                .description("Job-scoped multi-turn chat. Authorize with JWT from POST /api/v1/auth/login. "
                        + "Job id is the chat key; session id is output-only. One chat per job — DELETE then POST to start over. "
                        + "Title is Company - Title at create time, not a live job rename. "
                        + "POST /jobs/{id}/messages creates a turn and calls Gemini (~60s, 8/min, 429 + Retry-After). "
                        + "Always 201 when a turn is saved, including later turns. Response is latestTurn only. "
                        + "Last 16 turns go to the model; GET history pages the rest (size max 50, sort turnNumber ASC). "
                        + "GET/DELETE do not call Gemini. Empty history and empty list are 200. "
                        + "DELETE without a session is 200. 404 always means job not found or not yours, never chat missing. "
                        + "This is not POST /api/v1/ai/chat. 409 = concurrent send or resume pending. "
                        + "422 = resume parse failed. 502 = provider/blank reply. 503 = AI busy/circuit."));
    }
}

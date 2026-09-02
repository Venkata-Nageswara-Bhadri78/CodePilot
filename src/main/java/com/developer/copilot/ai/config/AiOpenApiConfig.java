package com.developer.copilot.ai.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * Isolates AI career-copilot endpoints into their own Swagger UI group. Profile-gated
 * so a mistaken springdoc enable in prod cannot publish this contract.
 */
@Configuration
@Profile("!prod & !production")
public class AiOpenApiConfig {

    @Bean
    public GroupedOpenApi aiOpenApi() {
        return GroupedOpenApi.builder()
                .group("ai")
                .displayName("AI Service")
                .pathsToMatch("/api/v1/ai/**")
                .addOpenApiCustomizer(AiOpenApiConfig::customize)
                .build();
    }

    private static void customize(OpenAPI openApi) {
        openApi.addServersItem(new Server().url("http://localhost:8080").description("Local"));
        openApi.addServersItem(new Server().url("https://api.yourdomain.com").description("Production host (docs only)"));
        if (openApi.getInfo() == null) {
            openApi.info(new Info().title("AI Service API"));
        }
        openApi.addTagsItem(new Tag()
                .name("AI Service")
                .description("Career copilot. Authorize with JWT from POST /api/v1/auth/login. "
                        + "POST /api/v1/ai/chat returns one JSON answer. POST /api/v1/ai/chat/stream is SSE "
                        + "(watch event done vs error; HTTP 200 is not success by itself). "
                        + "Chat without a stored resume uses empty context unless resumeId is sent. "
                        + "GET /resume-context is high-priority parse text (PII) and 404s if missing. "
                        + "GET /health and GET /config are configuration only, not a Gemini probe. "
                        + "429 = rate limit (Retry-After). 409 = resume still processing. "
                        + "422 = parse failed or empty text. 502 = provider. 503 = circuit/bulkhead. "
                        + "Job extract lives at POST /api/v1/job-extraction/parse."));
    }
}

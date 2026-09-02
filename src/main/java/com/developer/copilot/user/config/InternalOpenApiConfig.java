package com.developer.copilot.user.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod & !production")
public class InternalOpenApiConfig {

    private static final String INTERNAL_API_KEY = "InternalApiKey";
    private static final String BEARER = "Bearer Authentication";

    @Bean
    public GroupedOpenApi internalOpenApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .displayName("Internal Service API")
                .pathsToMatch("/api/v1/internal/**")
                .addOpenApiCustomizer(InternalOpenApiConfig::customize)
                .build();
    }

    private static void customize(OpenAPI openApi) {
        openApi.addServersItem(new Server().url("http://localhost:8080").description("Local"));
        if (openApi.getComponents() != null) {
            openApi.getComponents()
                    .addSecuritySchemes(
                            INTERNAL_API_KEY,
                            new SecurityScheme()
                                    .type(SecurityScheme.Type.APIKEY)
                                    .in(SecurityScheme.In.HEADER)
                                    .name("X-Internal-Api-Key")
                                    .description("Internal service-to-service API key. Required together with the user JWT, not instead of it."));
        }
        openApi.addSecurityItem(new SecurityRequirement()
                .addList(BEARER)
                .addList(INTERNAL_API_KEY));
        if (openApi.getInfo() == null) {
            openApi.info(new Info().title("Internal Service API"));
        }
        openApi.addTagsItem(new Tag()
                .name("Internal - Resume Parsing")
                .description("Backend-only. JWT identifies the user; X-Internal-Api-Key identifies the calling service. "
                        + "Ownership is still the JWT user — you cannot fetch another person's resume by id. "
                        + "200 only when status is COMPLETED. FAILED and still-PENDING are 422. "
                        + "On-demand parse (no row yet) can take several seconds. "
                        + "Payload includes rawText and contextText (PII). Not for the SPA. 429 if parse budget is spent."));
    }
}

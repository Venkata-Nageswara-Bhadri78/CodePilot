package com.developer.copilot.jobextraction.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * Isolates the Job Content Extraction endpoint into its own Swagger UI group. Profile-gated
 * so a mistaken springdoc enable in prod cannot publish this contract.
 */
@Configuration
@Profile("!prod & !production")
public class JobExtractionOpenApiConfig {

    @Bean
    public GroupedOpenApi jobExtractionOpenApi() {
        return GroupedOpenApi.builder()
                .group("job-extraction")
                .displayName("Job Content Extraction")
                .pathsToMatch("/api/v1/job-extraction/**")
                .addOpenApiCustomizer(JobExtractionOpenApiConfig::customize)
                .build();
    }

    private static void customize(OpenAPI openApi) {
        openApi.addServersItem(new Server().url("http://localhost:8080").description("Local"));
        openApi.addServersItem(new Server().url("https://api.yourdomain.com").description("Production host (docs only)"));
        if (openApi.getInfo() == null) {
            openApi.info(new Info().title("Job Content Extraction API"));
        }
        openApi.addTagsItem(new Tag()
                .name("Job Content Extraction")
                .description("Two-step product: 1) Authorize with JWT from POST /api/v1/auth/login. "
                        + "2) POST /api/v1/job-extraction/parse with sourceUrl + rawJobText. "
                        + "3) Edit data in your UI. 4) Save with POST /api/v1/jobs; omit requiresManualReview; "
                        + "use data.sourceUrl (canonical) not the raw request URL. "
                        + "Parse never writes rows. 200 is a preview. 409 = already in this user's notebook. "
                        + "502 = model/provider. 429 = parse budget spent (Retry-After). "
                        + "Try-it-out against a live model can take up to ~60 seconds."));
    }
}

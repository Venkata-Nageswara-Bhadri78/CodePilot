package com.developer.copilot.jobextraction.automatedjobextraction.config;

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
public class AutomatedJobExtractionOpenApiConfig {

    @Bean
    public GroupedOpenApi automatedJobExtractionOpenApi() {
        return GroupedOpenApi.builder()
                .group("automated-job-extraction")
                .displayName("Automated Job Extraction")
                .pathsToMatch("/api/v1/automated-job-extraction/**")
                .addOpenApiCustomizer(AutomatedJobExtractionOpenApiConfig::customize)
                .build();
    }

    private static void customize(OpenAPI openApi) {
        openApi.addServersItem(new Server().url("http://localhost:8080").description("Local"));
        openApi.addServersItem(new Server().url("https://api.yourdomain.com").description("Production host (docs only)"));
        if (openApi.getInfo() == null) {
            openApi.info(new Info().title("Automated Job Extraction API"));
        }
        openApi.addTagsItem(new Tag()
                .name("Automated Job Extraction")
                .description("Two-step product: 1) Authorize with JWT from POST /api/v1/auth/login. "
                        + "2) POST /api/v1/automated-job-extraction/parse with sourceUrl only. "
                        + "3) Edit data in your UI. 4) Save with POST /api/v1/jobs; omit requiresManualReview; "
                        + "use data.sourceUrl (canonical). Parse never writes rows. 200 is a preview. "
                        + "400 INVALID JOB URL = not a usable job posting. "
                        + "409 = already in this user's notebook. 403 = email not verified. "
                        + "502 = page fetch or model/provider. 503 = circuit/bulkhead. "
                        + "429 = parse budget spent (Retry-After)."));
    }
}

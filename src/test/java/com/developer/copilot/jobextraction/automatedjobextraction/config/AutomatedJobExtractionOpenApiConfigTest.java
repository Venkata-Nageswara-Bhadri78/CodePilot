package com.developer.copilot.jobextraction.automatedjobextraction.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springdoc.core.models.GroupedOpenApi;

class AutomatedJobExtractionOpenApiConfigTest {

    @Test
    void group_matchesAutomatedPaths() {
        GroupedOpenApi api = new AutomatedJobExtractionOpenApiConfig().automatedJobExtractionOpenApi();
        assertEquals("automated-job-extraction", api.getGroup());
        assertTrue(api.getPathsToMatch().contains("/api/v1/automated-job-extraction/**"));
    }

    @Test
    void config_isOffOnProdAndProductionProfiles() {
        Profile profile = AutomatedJobExtractionOpenApiConfig.class.getAnnotation(Profile.class);
        String expression = String.join(" & ", profile.value());
        assertTrue(expression.contains("!prod"));
        assertTrue(expression.contains("!production"));
    }
}

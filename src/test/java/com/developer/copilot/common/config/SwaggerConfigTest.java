package com.developer.copilot.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityRequirement;

class SwaggerConfigTest {

    @Test
    void openApi_registersBearerAndInternalKeyOnTheSameComponents() {
        OpenAPI api = new SwaggerConfig().openAPI();

        assertNotNull(api.getComponents().getSecuritySchemes().get(SwaggerConfig.BEARER_SCHEME));
        assertNotNull(api.getComponents().getSecuritySchemes().get(SwaggerConfig.INTERNAL_API_KEY_SCHEME));
        assertNotNull(api.getComponents().getResponses().get("ApiErrorResponse"));
        assertNotNull(api.getComponents().getSchemas().get("ApiResponse"));
        assertEquals(2, api.getServers().size());
        assertTrue(api.getInfo().getDescription().contains("There is no Common group"));
        assertTrue(api.getSecurity().stream()
                .anyMatch(requirement -> requirement.containsKey(SwaggerConfig.BEARER_SCHEME)));
        assertTrue(api.getSecurity().stream()
                .noneMatch(requirement -> requirement.containsKey(SwaggerConfig.INTERNAL_API_KEY_SCHEME)));
    }

    @Test
    void publicOpenApiGroup_isRemoved() {
        for (Method method : SwaggerConfig.class.getDeclaredMethods()) {
            assertFalse("publicOpenApi".equals(method.getName()),
                    "Public API group should not be registered (duplicates jobs/extraction).");
        }
    }

    @Test
    void publicAuthCustomizer_clearsSecurityOnLogin() {
        OpenAPI api = new OpenAPI();
        Paths paths = new Paths();
        PathItem login = new PathItem().post(new Operation()
                .security(List.of(new SecurityRequirement().addList(SwaggerConfig.BEARER_SCHEME))));
        PathItem jobs = new PathItem().get(new Operation()
                .security(List.of(new SecurityRequirement().addList(SwaggerConfig.BEARER_SCHEME))));
        paths.addPathItem("/api/v1/auth/login", login);
        paths.addPathItem("/api/v1/jobs", jobs);
        api.setPaths(paths);

        new SwaggerConfig().publicAuthPathsAnonymous().customise(api);

        assertTrue(login.getPost().getSecurity() == null || login.getPost().getSecurity().isEmpty());
        assertFalse(jobs.getGet().getSecurity() == null || jobs.getGet().getSecurity().isEmpty());
    }

    @Test
    void swaggerConfig_isOffOnProdAndProductionProfiles() {
        Profile profile = SwaggerConfig.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        String expression = String.join(" & ", profile.value());
        assertTrue(expression.contains("!prod"));
        assertTrue(expression.contains("!production"));
    }
}

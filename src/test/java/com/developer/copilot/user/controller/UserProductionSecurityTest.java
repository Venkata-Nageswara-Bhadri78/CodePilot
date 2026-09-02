package com.developer.copilot.user.controller;

import com.developer.copilot.auth.config.JsonAuthenticationEntryPoint;
import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.ratelimit.config.AuthRateLimitConfig;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.user.controller.internal.InternalResumeController;
import com.developer.copilot.user.service.ResumeParsingService;
import com.developer.copilot.user.service.UserProfileService;
import com.developer.copilot.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(controllers = {UserController.class, UserProfileController.class, InternalResumeController.class})
@ActiveProfiles("prod")
@TestPropertySource(properties = "APP_JWT_SECRET=test-secret-key-that-is-long-enough-for-hmac-sha256")
@Import({SecurityConfig.class, SecurityBeansConfig.class, JsonAuthenticationEntryPoint.class, AuthRateLimitConfig.class})
class UserProductionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserProfileService userProfileService;

    @MockitoBean
    private ResumeParsingService resumeParsingService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void swaggerUserDocs_onProduction_areNotPublic() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs/user")).andReturn();
        assertNotEquals(200, result.getResponse().getStatus());
    }

    @Test
    void swaggerInternalDocs_onProduction_areNotPublic() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs/internal")).andReturn();
        assertNotEquals(200, result.getResponse().getStatus());
    }

    @Test
    void swaggerApiDocs_onProduction_areNotPublic() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andReturn();
        assertNotEquals(200, result.getResponse().getStatus());
    }

    @Test
    void swaggerUi_onProduction_isNotPublic() throws Exception {
        MvcResult result = mockMvc.perform(get("/swagger-ui/index.html")).andReturn();
        assertNotEquals(200, result.getResponse().getStatus());
    }
}

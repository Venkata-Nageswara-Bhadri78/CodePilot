package com.developer.copilot.auth.controller;

import com.developer.copilot.auth.config.JsonAuthenticationEntryPoint;
import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.ratelimit.config.AuthRateLimitConfig;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.AuthClientAuthorities;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BrowserExtensionProbeController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class, JsonAuthenticationEntryPoint.class, AuthRateLimitConfig.class})
class BrowserExtensionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void unauthenticated_protectedRoutes_return401() throws Exception {
        mockMvc.perform(get("/api/v1/job-extraction/probe")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/jobs/probe")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/ai/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    void webFrontend_keepsAccessToAllProbes() throws Exception {
        mockMvc.perform(get("/api/v1/job-extraction/probe").with(webUser()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/automated-job-extraction/probe").with(webUser()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs/probe").with(webUser()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ai/probe").with(webUser()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/chat-assistant/probe").with(webUser()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/probe").with(webUser()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/internal/resumes/probe").with(webUser()))
                .andExpect(status().isOk());
    }

    @Test
    void extensionClient_canAccessJobExtractionOnly() throws Exception {
        mockMvc.perform(get("/api/v1/job-extraction/probe").with(extensionUser()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/automated-job-extraction/probe").with(extensionUser()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/job-extraction/probe")
                        .with(extensionUser())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void extensionClient_isDeniedOtherServices() throws Exception {
        assertForbidden("/api/v1/jobs/probe");
        assertForbidden("/api/v1/ai/probe");
        assertForbidden("/api/v1/chat-assistant/probe");
        assertForbidden("/api/v1/users/probe");
        assertForbidden("/api/v1/internal/resumes/probe");
        assertForbidden("/api/v1/auth/me-probe");
    }

    @Test
    void spoofedClientHeader_doesNotChangeAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/probe")
                        .header("X-Client", "browser-extension")
                        .header("X-Extension-Id", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                        .with(webUser()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/jobs/probe")
                        .header("X-Client", "web")
                        .with(extensionUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
    }

    private void assertForbidden(String path) throws Exception {
        mockMvc.perform(get(path).with(extensionUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
    }

    private static RequestPostProcessor webUser() {
        return user("john@example.com").roles("USER");
    }

    private static RequestPostProcessor extensionUser() {
        return user("john@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION));
    }
}

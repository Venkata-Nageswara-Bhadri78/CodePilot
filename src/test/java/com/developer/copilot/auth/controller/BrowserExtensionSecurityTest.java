package com.developer.copilot.auth.controller;

import com.developer.copilot.auth.config.JsonAuthenticationEntryPoint;
import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.ratelimit.config.AuthRateLimitConfig;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.AuthClientAuthorities;
import com.developer.copilot.auth.security.AuthOnboardingAuthorities;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        mockMvc.perform(get("/api/v1/users/resumes").with(webUser()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/internal/resumes/probe").with(webUser()))
                .andExpect(status().isOk());
    }

    @Test
    void extensionClient_canListResumesAndUseJobExtraction() throws Exception {
        mockMvc.perform(get("/api/v1/users/resumes").with(extensionUser()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/job-extraction/probe").with(extensionUser()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/automated-job-extraction/probe").with(extensionUser()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/job-extraction/probe")
                        .with(extensionUser())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/automated-job-extraction/probe")
                        .with(extensionUser())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void extensionClient_isDeniedOtherServices() throws Exception {
        assertForbidden(get("/api/v1/jobs/probe"));
        assertForbidden(post("/api/v1/jobs"));
        assertForbidden(get("/api/v1/job-drafts"));
        assertForbidden(get("/api/v1/job-drafts/4"));
        assertForbidden(put("/api/v1/job-drafts/4"));
        assertForbidden(post("/api/v1/job-drafts/4/save"));
        assertForbidden(delete("/api/v1/job-drafts/4"));
        assertForbidden(get("/api/v1/ai/probe"));
        assertForbidden(get("/api/v1/chat-assistant/probe"));
        assertForbidden(get("/api/v1/users/probe"));
        assertForbidden(post("/api/v1/users/resumes"));
        assertForbidden(get("/api/v1/users/resumes/12"));
        assertForbidden(delete("/api/v1/users/resumes/12"));
        assertForbidden(patch("/api/v1/users/resumes/12/high-priority"));
        assertForbidden(get("/api/v1/internal/resumes/probe"));
        assertForbidden(get("/api/v1/internal/resumes/parsed"));
        assertForbidden(get("/api/v1/internal/resumes/12/parsed"));
        assertForbidden(get("/api/v1/auth/me-probe"));
        assertForbidden(post("/api/v1/auth/extension-token"));
        assertForbidden(post("/api/v1/auth/logout"));
        assertForbidden(post("/api/v1/auth/logout-all"));
    }

    @Test
    void spoofedClientHeader_doesNotChangeAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/probe")
                        .header("X-Client", "browser-extension")
                        .header("X-Extension-Id", "lghllhgdcekacmdoolnekkkoffcmcaeo")
                        .header("Origin", "chrome-extension://lghllhgdcekacmdoolnekkkoffcmcaeo")
                        .with(webUser()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/resumes")
                        .header("X-Client", "browser-extension")
                        .header("X-Extension-Id", "lghllhgdcekacmdoolnekkkoffcmcaeo")
                        .header("Origin", "chrome-extension://lghllhgdcekacmdoolnekkkoffcmcaeo"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/jobs/probe")
                        .header("X-Client", "web")
                        .with(extensionUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));

        mockMvc.perform(get("/api/v1/users/resumes/12")
                        .header("X-Client", "web")
                        .header("Origin", "chrome-extension://lghllhgdcekacmdoolnekkkoffcmcaeo")
                        .with(extensionUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
    }

    private void assertForbidden(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(extensionUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
    }

    @Test
    void incompleteWebUser_cannotReachJobsOrExtraction() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/probe").with(incompleteUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthOnboardingAuthorities.INCOMPLETE_MESSAGE));
        mockMvc.perform(get("/api/v1/job-extraction/probe").with(incompleteUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthOnboardingAuthorities.INCOMPLETE_MESSAGE));
        mockMvc.perform(post("/api/v1/auth/extension-token").with(incompleteUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthOnboardingAuthorities.INCOMPLETE_MESSAGE));
    }

    private static RequestPostProcessor incompleteUser() {
        return user("john@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthOnboardingAuthorities.INCOMPLETE));
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

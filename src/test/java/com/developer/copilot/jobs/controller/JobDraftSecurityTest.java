package com.developer.copilot.jobs.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.AuthClientAuthorities;
import com.developer.copilot.jobs.service.JobDraftService;

@WebMvcTest(controllers = JobDraftController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class JobDraftSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobDraftService jobDraftService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void listDrafts_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/job-drafts"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(jobDraftService);
    }

    @Test
    void extensionClient_cannotUseDraftHttpApi() throws Exception {
        assertForbidden(get("/api/v1/job-drafts"));
        assertForbidden(get("/api/v1/job-drafts/4"));
        assertForbidden(put("/api/v1/job-drafts/4")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
        assertForbidden(post("/api/v1/job-drafts/4/save"));
        assertForbidden(delete("/api/v1/job-drafts/4"));
        verifyNoInteractions(jobDraftService);
    }

    @Test
    void webClient_canListDrafts() throws Exception {
        mockMvc.perform(get("/api/v1/job-drafts").with(user("john@example.com").roles("USER")))
                .andExpect(status().isOk());
    }

    private void assertForbidden(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(extensionUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
    }

    private static RequestPostProcessor extensionUser() {
        return user("john@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION));
    }
}

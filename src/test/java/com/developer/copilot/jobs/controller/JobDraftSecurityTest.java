package com.developer.copilot.jobs.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        verify(jobDraftService, never()).list(any());
    }

    @Test
    void extensionClient_cannotListDrafts() throws Exception {
        mockMvc.perform(get("/api/v1/job-drafts").with(extensionUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AuthClientAuthorities.FORBIDDEN_MESSAGE));
        verify(jobDraftService, never()).list(any());
    }

    @Test
    void webClient_canListDrafts() throws Exception {
        mockMvc.perform(get("/api/v1/job-drafts").with(user("john@example.com").roles("USER")))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor extensionUser() {
        return user("john@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION));
    }
}

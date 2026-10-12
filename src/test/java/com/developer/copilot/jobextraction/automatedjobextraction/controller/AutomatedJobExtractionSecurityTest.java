package com.developer.copilot.jobextraction.automatedjobextraction.controller;

import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.AuthClientAuthorities;
import com.developer.copilot.jobextraction.automatedjobextraction.service.AutomatedJobExtractionService;
import com.developer.copilot.jobextraction.persistence.JobAutoPersistResponse;
import com.developer.copilot.jobextraction.persistence.JobExtractionAutoPersistenceService;
import com.developer.copilot.jobs.dto.JobDraftResponse;
import com.developer.copilot.jobs.dto.JobResponse;
import com.developer.copilot.jobs.entity.JobDraftFailureCategory;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AutomatedJobExtractionController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class AutomatedJobExtractionSecurityTest {

    private static final String BODY = """
            {
              "sourceUrl": "https://example.com/jobs/123"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutomatedJobExtractionService automatedJobExtractionService;

    @MockitoBean
    private JobExtractionAutoPersistenceService jobExtractionAutoPersistenceService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void parseJobUrl_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verify(automatedJobExtractionService, never()).extractFromUrl(any());
    }

    @Test
    void parseJobUrl_garbageBearerToken_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenThrow(new JwtException("bad token"));

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .header("Authorization", "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verify(automatedJobExtractionService, never()).extractFromUrl(any());
    }

    @Test
    void parseJobUrl_unverifiedEmail_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenReturn(1L);
        User user = new User();
        user.setId(1L);
        user.setEnabled(true);
        user.setEmailVerified(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verify(automatedJobExtractionService, never()).extractFromUrl(any());
    }

    @Test
    void parseJobUrl_disabledUser_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenReturn(1L);
        User user = new User();
        user.setId(1L);
        user.setEnabled(false);
        user.setEmailVerified(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verify(automatedJobExtractionService, never()).extractFromUrl(any());
    }

    @Test
    void parseJobUrl_extensionClient_canAutoSave() throws Exception {
        when(jobExtractionAutoPersistenceService.persistAutomated(any())).thenReturn(
                JobAutoPersistResponse.drafted(JobDraftResponse.builder()
                        .id(4L)
                        .sourceUrl("https://example.com/jobs/123")
                        .failureCategory(JobDraftFailureCategory.VALIDATION)
                        .failureReason("title: Title cannot be blank.")
                        .build()));

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .with(extensionUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/123",
                                  "resume": 12,
                                  "isAuto": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Job could not be saved. Stored as a draft."))
                .andExpect(jsonPath("$.data.persistedAs").value("DRAFT"))
                .andExpect(jsonPath("$.data.draft.id").value(4));
        verify(jobExtractionAutoPersistenceService).persistAutomated(any());
        verify(automatedJobExtractionService, never()).extractFromUrl(any());
    }

    @Test
    void parseJobUrl_webClient_canAutoSave() throws Exception {
        when(jobExtractionAutoPersistenceService.persistAutomated(any())).thenReturn(
                JobAutoPersistResponse.saved(JobResponse.builder().id(9L).build()));

        mockMvc.perform(post("/api/v1/automated-job-extraction/parse")
                        .with(user("john@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor extensionUser() {
        return user("john@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION));
    }
}

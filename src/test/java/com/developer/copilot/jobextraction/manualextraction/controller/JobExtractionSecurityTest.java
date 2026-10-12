package com.developer.copilot.jobextraction.manualextraction.controller;

import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.AuthClientAuthorities;
import com.developer.copilot.jobextraction.manualextraction.service.JobExtractionService;
import com.developer.copilot.jobextraction.persistence.JobAutoPersistResponse;
import com.developer.copilot.jobextraction.persistence.JobExtractionAutoPersistenceService;
import com.developer.copilot.jobs.dto.JobResponse;

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

@WebMvcTest(controllers = JobExtractionController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class JobExtractionSecurityTest {

    private static final String BODY = """
            {
              "sourceUrl": "https://example.com/jobs/123",
              "rawJobText": "Full pasted job posting text."
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobExtractionService jobExtractionService;

    @MockitoBean
    private JobExtractionAutoPersistenceService jobExtractionAutoPersistenceService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void parseJobInfo_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verify(jobExtractionService, never()).extractJobInfo(any());
    }

    @Test
    void parseJobInfo_garbageBearerToken_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenThrow(new JwtException("bad token"));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .header("Authorization", "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verify(jobExtractionService, never()).extractJobInfo(any());
    }

    @Test
    void parseJobInfo_unverifiedEmail_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenReturn(1L);
        User user = new User();
        user.setId(1L);
        user.setEnabled(true);
        user.setEmailVerified(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verify(jobExtractionService, never()).extractJobInfo(any());
    }

    @Test
    void parseJobInfo_disabledUser_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenReturn(1L);
        User user = new User();
        user.setId(1L);
        user.setEnabled(false);
        user.setEmailVerified(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verify(jobExtractionService, never()).extractJobInfo(any());
    }

    @Test
    void parseJobInfo_extensionClient_canAutoSave() throws Exception {
        when(jobExtractionAutoPersistenceService.persistManual(any())).thenReturn(
                JobAutoPersistResponse.saved(JobResponse.builder()
                        .id(9L)
                        .sourceUrl("https://example.com/jobs/123")
                        .title("Engineer")
                        .company("Acme")
                        .build()));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .with(extensionUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUrl": "https://example.com/jobs/123",
                                  "rawJobText": "Full job posting text",
                                  "resume": 12,
                                  "isAuto": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Job created successfully."))
                .andExpect(jsonPath("$.data.persistedAs").value("JOB"));
        verify(jobExtractionAutoPersistenceService).persistManual(any());
    }

    @Test
    void parseJobInfo_webClient_canAutoSave() throws Exception {
        when(jobExtractionAutoPersistenceService.persistManual(any())).thenReturn(
                JobAutoPersistResponse.saved(JobResponse.builder().id(9L).build()));

        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .with(user("john@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void parseJobInfo_missingToken_extensionHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/job-extraction/parse")
                        .header("X-Client", "browser-extension")
                        .header("Origin", "chrome-extension://lghllhgdcekacmdoolnekkkoffcmcaeo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verify(jobExtractionAutoPersistenceService, never()).persistManual(any());
    }

    private static RequestPostProcessor extensionUser() {
        return user("john@example.com").authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority(AuthClientAuthorities.BROWSER_EXTENSION));
    }
}

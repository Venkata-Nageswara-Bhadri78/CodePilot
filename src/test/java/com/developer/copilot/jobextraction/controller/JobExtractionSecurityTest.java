package com.developer.copilot.jobextraction.controller;

import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.jobextraction.service.JobExtractionService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}

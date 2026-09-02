package com.developer.copilot.user.controller;

import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.user.controller.internal.InternalResumeController;
import com.developer.copilot.user.service.ResumeParsingService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalResumeController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class InternalResumeSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResumeParsingService resumeParsingService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void parsed_withoutJwt_returns401_evenIfInternalKeyPresent() throws Exception {
        mockMvc.perform(get("/api/v1/internal/resumes/parsed")
                        .header("X-Internal-Api-Key", "any-key"))
                .andExpect(status().isUnauthorized());
        verify(resumeParsingService, never()).getParsedResume(any());
    }

    @Test
    void parsedById_garbageBearerToken_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenThrow(new JwtException("bad token"));

        mockMvc.perform(get("/api/v1/internal/resumes/5/parsed")
                        .header("Authorization", "Bearer garbage")
                        .header("X-Internal-Api-Key", "any-key"))
                .andExpect(status().isUnauthorized());
        verify(resumeParsingService, never()).getParsedResume(any());
    }

    @Test
    void parsed_unverifiedEmail_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenReturn(1L);
        User user = new User();
        user.setId(1L);
        user.setEnabled(true);
        user.setEmailVerified(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/internal/resumes/parsed")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized());
        verify(resumeParsingService, never()).getParsedResume(any());
    }
}

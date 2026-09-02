package com.developer.copilot.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.developer.copilot.ai.dto.request.AiMode;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.config.SecurityBeansConfig;
import com.developer.copilot.auth.config.SecurityConfig;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.common.security.CurrentUserService;

import io.jsonwebtoken.JwtException;

@WebMvcTest(controllers = AiController.class)
@Import({SecurityConfig.class, SecurityBeansConfig.class})
class AiSecurityTest {

    private static final String CHAT_BODY = """
            {
              "prompt": "Help me prepare"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiService aiService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void chat_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized."));
        verify(aiService, never()).chat(any(), any());
    }

    @Test
    void chat_garbageBearerToken_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenThrow(new JwtException("bad token"));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_BODY))
                .andExpect(status().isUnauthorized());
        verify(aiService, never()).chat(any(), any());
    }

    @Test
    void chat_unverifiedEmail_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenReturn(1L);
        User user = enabledUser(false, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_BODY))
                .andExpect(status().isUnauthorized());
        verify(aiService, never()).chat(any(), any());
    }

    @Test
    void chat_disabledUser_returns401() throws Exception {
        when(jwtService.extractUserId(any())).thenReturn(1L);
        User user = enabledUser(true, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_BODY))
                .andExpect(status().isUnauthorized());
        verify(aiService, never()).chat(any(), any());
    }

    @Test
    void health_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/ai/health"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized."));
        verify(aiService, never()).getActiveModel();
    }

    @Test
    void resumeContext_withoutAuthorization_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/ai/resume-context"))
                .andExpect(status().isUnauthorized());
        verify(aiService, never()).getResumeContext();
    }

    @Test
    void chat_authenticated_ignoresUnknownJsonFields() throws Exception {
        User user = enabledUser(true, true);
        when(jwtService.extractUserId("good")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(eq("good"), any(User.class))).thenReturn(true);
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(aiService.chat(any(), eq(1L))).thenReturn(AiChatResponse.builder()
                .content("ok")
                .model("gemini-flash-latest")
                .finishReason("STOP")
                .mode(AiMode.GENERAL_CHAT)
                .timestamp(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/api/v1/ai/chat")
                        .header("Authorization", "Bearer good")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "Help me prepare",
                                  "unexpectedField": "ignored"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private static User enabledUser(boolean emailVerified, boolean enabled) {
        User user = new User();
        user.setId(1L);
        user.setEmail("candidate@example.com");
        user.setEmailVerified(emailVerified);
        user.setEnabled(enabled);
        return user;
    }
}

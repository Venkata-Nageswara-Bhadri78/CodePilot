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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.developer.copilot.ai.dto.request.AiMode;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.dto.response.AiStreamChunk;
import com.developer.copilot.ai.exception.AiResumePendingException;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.user.exception.ResumeNotFoundException;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AiService aiService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private AiController aiController;

    private MockMvc mockMvc;

    private static final String USER_EMAIL = "candidate@example.com";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(aiController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void chat_validRequest_returns200() throws Exception {
        stubAuthenticatedUser();
        when(aiService.chat(any(), eq(USER_EMAIL))).thenReturn(AiChatResponse.builder()
                .content("Hello from AI")
                .model("gemini-flash-latest")
                .finishReason("STOP")
                .mode(AiMode.GENERAL_CHAT)
                .timestamp(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "Help me prepare for interviews"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("Hello from AI"))
                .andExpect(jsonPath("$.data.model").value("gemini-flash-latest"));
    }

    @Test
    void chat_blankPrompt_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(aiService, never()).chat(any(), any());
    }

    @Test
    void chat_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid"))
                .andExpect(status().isBadRequest());

        verify(aiService, never()).chat(any(), any());
    }

    @Test
    void chat_unauthenticated_returns401AndDoesNotCallService() throws Exception {
        when(currentUserService.getCurrentUser())
                .thenThrow(new InvalidCredentialsException("User is not authenticated."));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "Hello"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verify(aiService, never()).chat(any(), any());
    }

    @Test
    void chat_providerFailure_returns502() throws Exception {
        stubAuthenticatedUser();
        when(aiService.chat(any(), eq(USER_EMAIL)))
                .thenThrow(new AiServiceException("An unexpected error occurred while communicating with the AI model. Please try again."));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "Hello"
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void chat_missingJob_returns404() throws Exception {
        stubAuthenticatedUser();
        when(aiService.chat(any(), eq(USER_EMAIL)))
                .thenThrow(new JobNotFoundException("Job not found."));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "Review this role",
                                  "jobId": 999
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void resumeContext_success_returns200() throws Exception {
        stubAuthenticatedUser();
        when(aiService.getResumeContext(USER_EMAIL)).thenReturn("parsed resume");

        mockMvc.perform(get("/api/v1/ai/resume-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("parsed resume"));
    }

    @Test
    void resumeContext_missingResume_returns404() throws Exception {
        stubAuthenticatedUser();
        when(aiService.getResumeContext(USER_EMAIL)).thenThrow(new ResumeNotFoundException());

        mockMvc.perform(get("/api/v1/ai/resume-context"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resumeContext_pending_returns409() throws Exception {
        stubAuthenticatedUser();
        when(aiService.getResumeContext(USER_EMAIL))
                .thenThrow(new AiResumePendingException("Your resume is still being processed. Please try again in a few moments."));

        mockMvc.perform(get("/api/v1/ai/resume-context"))
                .andExpect(status().isConflict());
    }

    @Test
    void health_returnsConfigurationMetadata() throws Exception {
        when(aiService.getActiveModel()).thenReturn("gemini-flash-latest (Provider: gemini)");

        mockMvc.perform(get("/api/v1/ai/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.healthCheckType").value("configuration"))
                .andExpect(jsonPath("$.data.streamingSupported").value(true))
                .andExpect(jsonPath("$.message").value(
                        "AI configuration metadata (does not verify provider connectivity)."));
    }

    @Test
    void streamChat_mapsMessageDoneAndErrorEvents() throws Exception {
        stubAuthenticatedUser();
        when(aiService.streamChat(any(), eq(USER_EMAIL))).thenReturn(Flux.just(
                AiStreamChunk.builder().content("Hi").isCompleted(false).model("gemini-flash-latest").build(),
                AiStreamChunk.builder().content("").isCompleted(true).finishReason("STOP").model("gemini-flash-latest").build()
        ));

        mockMvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "prompt": "Hello stream"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void chat_oversizedPrompt_returns400() throws Exception {
        String oversized = "x".repeat(8001);

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "%s"
                                }
                                """.formatted(oversized)))
                .andExpect(status().isBadRequest());

        verify(aiService, never()).chat(any(), any());
    }

    private void stubAuthenticatedUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail(USER_EMAIL);
        when(currentUserService.getCurrentUser()).thenReturn(user);
    }
}

package com.developer.copilot.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.developer.copilot.ai.dto.request.AiMode;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.dto.response.AiStreamChunk;
import com.developer.copilot.ai.exception.AiResumePendingException;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.exception.AiUnavailableException;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.ResumeParsingException;
import com.developer.copilot.user.exception.UserProfileNotFoundException;

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
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(aiController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void chat_validRequest_returns200() throws Exception {
        stubAuthenticatedUser();
        when(aiService.chat(any(), eq(USER_ID))).thenReturn(AiChatResponse.builder()
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
    void chat_missingPrompt_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "GENERAL_CHAT"
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
    void chat_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());

        verify(aiService, never()).chat(any(), any());
    }

    @Test
    void chat_invalidMode_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "Hello",
                                  "mode": "NOPE"
                                }
                                """))
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
        when(aiService.chat(any(), eq(USER_ID)))
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
    void chat_circuitOpen_returns503() throws Exception {
        stubAuthenticatedUser();
        when(aiService.chat(any(), eq(USER_ID)))
                .thenThrow(new AiUnavailableException("The AI service is temporarily unavailable. Please try again shortly."));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "Hello"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "The AI service is temporarily unavailable. Please try again shortly."));
    }

    @Test
    void chat_unexpectedError_returns500() throws Exception {
        stubAuthenticatedUser();
        when(aiService.chat(any(), eq(USER_ID))).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "Hello"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong."));
    }

    @Test
    void chat_missingJob_returns404() throws Exception {
        stubAuthenticatedUser();
        when(aiService.chat(any(), eq(USER_ID)))
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
    void chat_missingResumeId_returns404() throws Exception {
        stubAuthenticatedUser();
        when(aiService.chat(any(), eq(USER_ID))).thenThrow(new ResumeNotFoundException());

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "Review my resume",
                                  "resumeId": 5
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void chat_missingProfile_returns404() throws Exception {
        stubAuthenticatedUser();
        when(aiService.chat(any(), eq(USER_ID))).thenThrow(new UserProfileNotFoundException());

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "Hello",
                                  "resumeId": 5
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void chat_pendingResume_returns409() throws Exception {
        stubAuthenticatedUser();
        when(aiService.chat(any(), eq(USER_ID)))
                .thenThrow(new AiResumePendingException("Your resume is still being processed. Please try again in a few moments."));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "Hello"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void chat_parseFailed_returns422() throws Exception {
        stubAuthenticatedUser();
        when(aiService.chat(any(), eq(USER_ID)))
                .thenThrow(new ResumeParsingException("Your resume could not be parsed. Please upload a different PDF and try again."));

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "Hello"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void resumeContext_success_returns200() throws Exception {
        stubAuthenticatedUser();
        when(aiService.getResumeContext()).thenReturn("parsed resume");

        mockMvc.perform(get("/api/v1/ai/resume-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("parsed resume"));
    }

    @Test
    void resumeContext_missingResume_returns404() throws Exception {
        stubAuthenticatedUser();
        when(aiService.getResumeContext()).thenThrow(new ResumeNotFoundException());

        mockMvc.perform(get("/api/v1/ai/resume-context"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resumeContext_pending_returns409() throws Exception {
        stubAuthenticatedUser();
        when(aiService.getResumeContext())
                .thenThrow(new AiResumePendingException("Your resume is still being processed. Please try again in a few moments."));

        mockMvc.perform(get("/api/v1/ai/resume-context"))
                .andExpect(status().isConflict());
    }

    @Test
    void resumeContext_parseFailed_returns422() throws Exception {
        stubAuthenticatedUser();
        when(aiService.getResumeContext())
                .thenThrow(new ResumeParsingException("Resume text was empty. Re-upload the PDF."));

        mockMvc.perform(get("/api/v1/ai/resume-context"))
                .andExpect(status().isUnprocessableEntity());
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
    void config_returnsSamePayloadAsHealth() throws Exception {
        when(aiService.getActiveModel()).thenReturn("gemini-flash-latest (Provider: gemini)");

        mockMvc.perform(get("/api/v1/ai/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.healthCheckType").value("configuration"))
                .andExpect(jsonPath("$.data.activeModel").value("gemini-flash-latest (Provider: gemini)"));
    }

    @Test
    void streamChat_mapsMessageThenDoneEvents() throws Exception {
        stubAuthenticatedUser();
        when(aiService.streamChat(any(), eq(USER_ID))).thenReturn(Flux.just(
                AiStreamChunk.builder().content("Hi").isCompleted(false).model("gemini-flash-latest").build(),
                AiStreamChunk.builder().content("").isCompleted(true).finishReason("STOP").model("gemini-flash-latest").build()
        ));

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "prompt": "Hello stream"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("event:message")))
                .andExpect(content().string(Matchers.containsString("event:done")));
    }

    @Test
    void streamChat_terminalError_mapsErrorEvent() throws Exception {
        stubAuthenticatedUser();
        when(aiService.streamChat(any(), eq(USER_ID))).thenReturn(Flux.just(
                AiStreamChunk.builder()
                        .content("AI Service Error: timeout")
                        .isCompleted(true)
                        .finishReason("ERROR")
                        .model("gemini-flash-latest")
                        .build()
        ));

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "prompt": "Hello stream"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("event:error")));
    }

    @Test
    void streamChat_completedWithoutFinishReason_mapsDoneEvent() throws Exception {
        stubAuthenticatedUser();
        when(aiService.streamChat(any(), eq(USER_ID))).thenReturn(Flux.just(
                AiStreamChunk.builder().content("").isCompleted(true).finishReason(null).model("gemini-flash-latest").build()
        ));

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "prompt": "Hello stream"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("event:done")));
    }

    @Test
    void streamChat_missingJobBeforeSse_returns404Json() throws Exception {
        stubAuthenticatedUser();
        when(aiService.streamChat(any(), eq(USER_ID)))
                .thenThrow(new JobNotFoundException("Job not found."));

        mockMvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt": "Review this role",
                                  "jobId": 999
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
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
        user.setId(USER_ID);
        user.setEmail(USER_EMAIL);
        when(currentUserService.getCurrentUser()).thenReturn(user);
    }
}

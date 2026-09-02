package com.developer.copilot.chatassistant.controller;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.developer.copilot.ai.exception.AiResumePendingException;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.ai.exception.AiUnavailableException;
import com.developer.copilot.chatassistant.dto.response.ChatMessageResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionListResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionSummaryResponse;
import com.developer.copilot.chatassistant.dto.response.SendChatMessageResponse;
import com.developer.copilot.chatassistant.exception.ChatAssistantExceptionHandler;
import com.developer.copilot.chatassistant.exception.ChatConflictException;
import com.developer.copilot.chatassistant.service.ChatAssistantService;
import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.user.exception.ResumeParsingException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatAssistantControllerTest {

    @Mock
    private ChatAssistantService chatAssistantService;

    @InjectMocks
    private ChatAssistantController chatAssistantController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(chatAssistantController)
                .setControllerAdvice(new ChatAssistantExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendMessage_validRequest_returns201() throws Exception {
        SendChatMessageResponse result = SendChatMessageResponse.builder()
                .chatSessionId(15L)
                .chatTitle("Amazon - SDE 1")
                .latestTurn(ChatMessageResponse.builder()
                        .id(1L)
                        .turnNumber(1)
                        .userPrompt("Hello")
                        .aiResponse("Hi there!")
                        .createdAt(LocalDateTime.now())
                        .build())
                .build();
        when(chatAssistantService.sendMessage(eq(1L), any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Hello"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.latestTurn").exists())
                .andExpect(jsonPath("$.data.latestTurn.turnNumber").value(1));
    }

    @Test
    void sendMessage_unknownJsonFields_areIgnored() throws Exception {
        SendChatMessageResponse result = SendChatMessageResponse.builder()
                .chatSessionId(15L)
                .chatTitle("Amazon - SDE 1")
                .latestTurn(ChatMessageResponse.builder().id(1L).turnNumber(1).userPrompt("Hello").aiResponse("Hi").build())
                .build();
        when(chatAssistantService.sendMessage(eq(1L), any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Hello","resumeId":99,"extra":true}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void sendMessage_promptAt8000Chars_returns201() throws Exception {
        SendChatMessageResponse result = SendChatMessageResponse.builder()
                .chatSessionId(15L)
                .latestTurn(ChatMessageResponse.builder().turnNumber(1).build())
                .build();
        when(chatAssistantService.sendMessage(eq(1L), any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"%s"}
                                """.formatted("a".repeat(8000))))
                .andExpect(status().isCreated());
    }

    @Test
    void sendMessage_blankPrompt_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Prompt cannot be blank.")));
    }

    @Test
    void sendMessage_whitespacePrompt_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessage_promptExceeds8000Chars_returns400() throws Exception {
        String oversizedPrompt = "a".repeat(8001);

        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"%s"}
                                """.formatted(oversizedPrompt)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void sendMessage_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed JSON."));
    }

    @Test
    void sendMessage_jobNotFound_returns404() throws Exception {
        when(chatAssistantService.sendMessage(eq(1L), any()))
                .thenThrow(new JobNotFoundException("Job not found."));

        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Hello"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void sendMessage_aiServiceFails_returns502() throws Exception {
        when(chatAssistantService.sendMessage(eq(1L), any()))
                .thenThrow(new AiServiceException("AI provider unavailable."));

        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Hello"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void sendMessage_aiUnavailable_returns503() throws Exception {
        when(chatAssistantService.sendMessage(eq(1L), any()))
                .thenThrow(new AiUnavailableException("The AI service is busy. Please try again shortly."));

        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Hello"}
                                """))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void sendMessage_resumePending_returns409() throws Exception {
        when(chatAssistantService.sendMessage(eq(1L), any()))
                .thenThrow(new AiResumePendingException("Your resume is still being processed. Please try again in a few moments."));

        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Hello"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void sendMessage_resumeParseFailed_returns422() throws Exception {
        when(chatAssistantService.sendMessage(eq(1L), any()))
                .thenThrow(new ResumeParsingException("Your resume could not be parsed. Please upload a different PDF and try again."));

        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Hello"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sendMessage_conflict_returns409() throws Exception {
        when(chatAssistantService.sendMessage(eq(1L), any()))
                .thenThrow(new ChatConflictException("This chat was updated at the same time. Please retry."));

        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Hello"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This chat was updated at the same time. Please retry."));
    }

    @Test
    void sendMessage_dataIntegrityViolation_returns409() throws Exception {
        when(chatAssistantService.sendMessage(eq(1L), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Hello"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The request conflicts with existing data. Please retry."));
    }

    @Test
    void sendMessage_priorTurnsIllegalArgument_returns400() throws Exception {
        when(chatAssistantService.sendMessage(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("Prior turns cannot exceed 40 entries."));

        mockMvc.perform(post("/api/v1/chat-assistant/jobs/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Hello"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Prior turns cannot exceed 40 entries."));
    }

    @Test
    void getChatHistory_fullHistory_returns200() throws Exception {
        ChatSessionResponse history = ChatSessionResponse.builder()
                .chatSessionId(15L)
                .jobId(1L)
                .chatTitle("Amazon - SDE 1")
                .messages(List.of(
                        ChatMessageResponse.builder().id(1L).turnNumber(1).userPrompt("Q1").aiResponse("A1").build(),
                        ChatMessageResponse.builder().id(2L).turnNumber(2).userPrompt("Q2").aiResponse("A2").build()))
                .page(0)
                .size(50)
                .totalElements(2L)
                .totalPages(1)
                .build();
        when(chatAssistantService.getChatHistory(eq(1L), any(Pageable.class))).thenReturn(history);

        mockMvc.perform(get("/api/v1/chat-assistant/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2));
    }

    @Test
    void getChatHistory_forwardsPageAndSize() throws Exception {
        when(chatAssistantService.getChatHistory(eq(1L), any(Pageable.class)))
                .thenReturn(ChatSessionResponse.builder().jobId(1L).messages(List.of()).build());

        mockMvc.perform(get("/api/v1/chat-assistant/jobs/1").param("page", "1").param("size", "10"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(chatAssistantService).getChatHistory(eq(1L), captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(10, captor.getValue().getPageSize());
        assertEquals("turnNumber: ASC", captor.getValue().getSort().toString());
    }

    @Test
    void getChatHistory_illegalPage_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/chat-assistant/jobs/1").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getChatHistory_sizeZero_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/chat-assistant/jobs/1").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getChatHistory_hugeSize_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/chat-assistant/jobs/1").param("size", "500000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getChatHistory_nonNumericJobId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/chat-assistant/jobs/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid job id."));
    }

    @Test
    void getChatHistory_noChatStarted_returns200WithEmptyResult() throws Exception {
        ChatSessionResponse history = ChatSessionResponse.builder()
                .chatSessionId(null)
                .jobId(1L)
                .chatTitle(null)
                .messages(Collections.emptyList())
                .build();
        when(chatAssistantService.getChatHistory(eq(1L), any(Pageable.class))).thenReturn(history);

        mockMvc.perform(get("/api/v1/chat-assistant/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chatSessionId").isEmpty())
                .andExpect(jsonPath("$.data.messages").isArray())
                .andExpect(jsonPath("$.data.messages.length()").value(0));
    }

    @Test
    void getChatHistory_jobNotFound_returns404() throws Exception {
        when(chatAssistantService.getChatHistory(eq(1L), any(Pageable.class)))
                .thenThrow(new JobNotFoundException("Job not found."));

        mockMvc.perform(get("/api/v1/chat-assistant/jobs/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listMyChats_withSummaries_returns200() throws Exception {
        ChatSessionListResponse chats = ChatSessionListResponse.builder()
                .chats(List.of(
                        ChatSessionSummaryResponse.builder().chatSessionId(15L).jobId(1L).chatTitle("Amazon - SDE 1").build(),
                        ChatSessionSummaryResponse.builder().chatSessionId(16L).jobId(2L).chatTitle("Google - Backend").build()))
                .page(0)
                .size(50)
                .totalElements(2L)
                .totalPages(1)
                .build();
        when(chatAssistantService.listMyChats(any(Pageable.class))).thenReturn(chats);

        mockMvc.perform(get("/api/v1/chat-assistant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chats.length()").value(2));
    }

    @Test
    void listMyChats_noChats_returns200WithEmptyList() throws Exception {
        when(chatAssistantService.listMyChats(any(Pageable.class))).thenReturn(
                ChatSessionListResponse.builder()
                        .chats(Collections.emptyList())
                        .page(0)
                        .size(50)
                        .totalElements(0L)
                        .totalPages(0)
                        .build());

        mockMvc.perform(get("/api/v1/chat-assistant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chats").isArray())
                .andExpect(jsonPath("$.data.chats.length()").value(0));
    }

    @Test
    void listMyChats_hugeSize_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/chat-assistant").param("size", "500000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteChat_success_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/chat-assistant/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void deleteChat_jobNotFound_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new JobNotFoundException("Job not found."))
                .when(chatAssistantService).deleteChat(1L);

        mockMvc.perform(delete("/api/v1/chat-assistant/jobs/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}

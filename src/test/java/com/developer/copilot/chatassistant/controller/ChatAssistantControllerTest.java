package com.developer.copilot.chatassistant.controller;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.chatassistant.dto.response.ChatMessageResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionSummaryResponse;
import com.developer.copilot.chatassistant.dto.response.SendChatMessageResponse;
import com.developer.copilot.chatassistant.service.ChatAssistantService;
import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.jobs.exception.JobNotFoundException;

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
                .setControllerAdvice(new GlobalExceptionHandler())
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
    void sendMessage_jobNotFound_returns404() throws Exception {
        when(chatAssistantService.sendMessage(eq(1L), any()))
                .thenThrow(new JobNotFoundException("Job not found with id: 1"));

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
                .thenThrow(new JobNotFoundException("Job not found with id: 1"));

        mockMvc.perform(get("/api/v1/chat-assistant/jobs/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listMyChats_withSummaries_returns200() throws Exception {
        List<ChatSessionSummaryResponse> chats = List.of(
                ChatSessionSummaryResponse.builder().chatSessionId(15L).jobId(1L).chatTitle("Amazon - SDE 1").build(),
                ChatSessionSummaryResponse.builder().chatSessionId(16L).jobId(2L).chatTitle("Google - Backend").build());
        when(chatAssistantService.listMyChats()).thenReturn(chats);

        mockMvc.perform(get("/api/v1/chat-assistant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void listMyChats_noChats_returns200WithEmptyList() throws Exception {
        when(chatAssistantService.listMyChats()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/chat-assistant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
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
        org.mockito.Mockito.doThrow(new JobNotFoundException("Job not found with id: 1"))
                .when(chatAssistantService).deleteChat(1L);

        mockMvc.perform(delete("/api/v1/chat-assistant/jobs/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}

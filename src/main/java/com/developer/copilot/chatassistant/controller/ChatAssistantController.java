package com.developer.copilot.chatassistant.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.chatassistant.dto.request.SendChatMessageRequest;
import com.developer.copilot.chatassistant.dto.response.ChatSessionResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionSummaryResponse;
import com.developer.copilot.chatassistant.dto.response.SendChatMessageResponse;
import com.developer.copilot.chatassistant.service.ChatAssistantService;
import com.developer.copilot.common.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/chat-assistant")
@RequiredArgsConstructor
@Validated
public class ChatAssistantController {

    private final ChatAssistantService chatAssistantService;

    @PostMapping("/jobs/{jobId}/messages")
    public ResponseEntity<ApiResponse<SendChatMessageResponse>> sendMessage(
            @PathVariable Long jobId,
            @Valid @RequestBody SendChatMessageRequest request) {

        SendChatMessageResponse result = chatAssistantService.sendMessage(jobId, request);

        ApiResponse<SendChatMessageResponse> response = ApiResponse.<SendChatMessageResponse>builder()
                .success(true)
                .message("Message sent successfully.")
                .data(result)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> getChatHistory(@PathVariable Long jobId) {
        ChatSessionResponse history = chatAssistantService.getChatHistory(jobId);

        ApiResponse<ChatSessionResponse> response = ApiResponse.<ChatSessionResponse>builder()
                .success(true)
                .message("Chat history retrieved successfully.")
                .data(history)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatSessionSummaryResponse>>> listMyChats() {
        List<ChatSessionSummaryResponse> chats = chatAssistantService.listMyChats();

        ApiResponse<List<ChatSessionSummaryResponse>> response = ApiResponse.<List<ChatSessionSummaryResponse>>builder()
                .success(true)
                .message("Chats retrieved successfully.")
                .data(chats)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<Void>> deleteChat(@PathVariable Long jobId) {
        chatAssistantService.deleteChat(jobId);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Chat deleted successfully.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }
}

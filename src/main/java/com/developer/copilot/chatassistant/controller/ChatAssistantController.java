package com.developer.copilot.chatassistant.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.chatassistant.dto.request.SendChatMessageRequest;
import com.developer.copilot.chatassistant.dto.response.ChatSessionResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionSummaryResponse;
import com.developer.copilot.chatassistant.dto.response.SendChatMessageResponse;
import com.developer.copilot.chatassistant.service.ChatAssistantService;
import com.developer.copilot.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Chat Assistant", description = "Multi-turn AI conversations about saved jobs")
@RestController
@RequestMapping("/api/v1/chat-assistant")
@RequiredArgsConstructor
@Validated
public class ChatAssistantController {

    private final ChatAssistantService chatAssistantService;

    @Operation(
            summary = "Send a message to the job chat",
            description = "Sends a new prompt about the specified job. Creates a chat session on the first call. "
                    + "Returns only the newly created turn - not the full history."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Message sent and AI response received"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Prompt is blank or exceeds 8000 characters"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or does not belong to the current user"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "AI provider failed to respond")
    })
    @PostMapping("/jobs/{jobId}/messages")
    public ResponseEntity<ApiResponse<SendChatMessageResponse>> sendMessage(
            @Parameter(description = "ID of the job this chat belongs to", example = "42", required = true)
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

    @Operation(
            summary = "Get chat history for a job",
            description = "Returns a page of turns in chronological order. Returns 200 with empty messages if no chat "
                    + "has been started yet - this is not an error."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chat history returned (may have empty messages if no chat started)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or does not belong to the current user")
    })
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> getChatHistory(
            @Parameter(description = "ID of the job this chat belongs to", example = "42", required = true)
            @PathVariable Long jobId,
            @Parameter(description = "Zero-based page index of turns to return")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of turns per page")
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("turnNumber").ascending());
        ChatSessionResponse history = chatAssistantService.getChatHistory(jobId, pageable);

        ApiResponse<ChatSessionResponse> response = ApiResponse.<ChatSessionResponse>builder()
                .success(true)
                .message("Chat history retrieved successfully.")
                .data(history)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "List all my chat sessions",
            description = "Returns summaries of all job chats for the current user, newest first. "
                    + "Returns empty list if no chats exist."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chat list returned (may be empty)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
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

    @Operation(
            summary = "Delete a job's entire chat history",
            description = "Permanently deletes all messages and the session for the given job. "
                    + "Idempotent - returns 200 even if no chat has been started for that job."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chat deleted successfully (or already absent)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the current user")
    })
    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<Void>> deleteChat(
            @Parameter(description = "ID of the job this chat belongs to", example = "42", required = true)
            @PathVariable Long jobId) {
        chatAssistantService.deleteChat(jobId);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Chat deleted successfully.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }
}

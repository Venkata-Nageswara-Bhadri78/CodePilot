package com.developer.copilot.chatassistant.controller;

import java.time.LocalDateTime;

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
import com.developer.copilot.chatassistant.dto.response.ChatSessionListResponse;
import com.developer.copilot.chatassistant.dto.response.ChatSessionResponse;
import com.developer.copilot.chatassistant.dto.response.SendChatMessageResponse;
import com.developer.copilot.chatassistant.service.ChatAssistantService;
import com.developer.copilot.chatassistant.util.ChatAssistantLimits;
import com.developer.copilot.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(
        name = "Chat Assistant",
        description = "Multi-turn AI conversations about one saved job. "
                + "Obtain a JWT from POST /api/v1/auth/login, then Authorize. "
                + "Job id is the chat key; session id is output-only. "
                + "This is not POST /api/v1/ai/chat — that is general copilot. "
                + "Only the last 16 turns are sent to the model; GET history pages the rest.")
@RestController
@RequestMapping("/api/v1/chat-assistant")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "Bearer Authentication")
public class ChatAssistantController {

    private static final String JOB_ID_DESCRIPTION =
            "Saved job owned by the current user. Foreign or missing ids return 404 with the same message.";

    private static final String ERROR_JSON =
            "{\"success\":false,\"message\":\"A human-readable description of what went wrong.\","
                    + "\"data\":null,\"timestamp\":\"2026-01-01T12:00:00\"}";

    private final ChatAssistantService chatAssistantService;

    @Operation(
            summary = "Send a message to the job chat",
            description = "Sends a new prompt about the specified job. Creates a chat session on the first call. "
                    + "Always returns HTTP 201 when a **message** (turn) is created — including turn 20, not only the first. "
                    + "Response is only the new turn; poll GET history for the thread. "
                    + "Calls Gemini (can take ~60s). Last 16 turns are sent to the model. "
                    + "8 sends/minute per user and IP (429 + Retry-After)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Turn created and AI reply stored"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Blank/oversized prompt or illegal JSON",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the current user",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Concurrent send collision, or resume still parsing",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Resume parse failed or empty",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many send requests. Retry-After is set.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "AI provider failed, timed out, or returned a blank reply",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "AI circuit open or bulkhead full",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON)))
    })
    @PostMapping("/jobs/{jobId}/messages")
    public ResponseEntity<ApiResponse<SendChatMessageResponse>> sendMessage(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
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
            description = "Returns a page of turns in chronological order (fixed sort: turnNumber ASC). "
                    + "Returns 200 with empty messages if no chat has been started yet — this is not an error. "
                    + "Does not call Gemini. page is 0-based; size default 50, max 50."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chat history returned (may have empty messages if no chat started)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Illegal page/size or invalid job id",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the current user",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON)))
    })
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> getChatHistory(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @PathVariable Long jobId,
            @Parameter(description = "Zero-based page index of turns to return")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Turns per page (default 50, max 50)")
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = historyPage(page, size);
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
            summary = "List my chat sessions",
            description = "Paged summaries of job chats for the current user, newest-updated first. "
                    + "updatedAt is the session row (bumped when a message is saved). "
                    + "page is 0-based; size default 50, max 50. Empty chats array if none exist. "
                    + "Does not call Gemini."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chat list returned (may be empty)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Illegal page/size",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<ChatSessionListResponse>> listMyChats(
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sessions per page (default 50, max 50)")
            @RequestParam(defaultValue = "50") int size) {
        ChatSessionListResponse chats = chatAssistantService.listMyChats(listPage(page, size));

        ApiResponse<ChatSessionListResponse> response = ApiResponse.<ChatSessionListResponse>builder()
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
                    + "Idempotent — returns 200 even if no chat has been started for that job. "
                    + "Does not delete the job itself. Does not call Gemini."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chat deleted successfully (or already absent)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid job id",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found or not owned by the current user",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = ERROR_JSON)))
    })
    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<Void>> deleteChat(
            @Parameter(description = JOB_ID_DESCRIPTION, example = "42", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @PathVariable Long jobId) {
        chatAssistantService.deleteChat(jobId);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(true)
                .message("Chat deleted successfully.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    private static Pageable historyPage(int page, int size) {
        requireValidPage(page, size);
        return PageRequest.of(page, size, Sort.by("turnNumber").ascending());
    }

    private static Pageable listPage(int page, int size) {
        requireValidPage(page, size);
        return PageRequest.of(page, size);
    }

    private static void requireValidPage(int page, int size) {
        if (page < 0 || size < 1 || size > ChatAssistantLimits.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "page must be >= 0 and size must be between 1 and " + ChatAssistantLimits.MAX_PAGE_SIZE + ".");
        }
    }
}

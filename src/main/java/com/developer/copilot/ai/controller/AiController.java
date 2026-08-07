package com.developer.copilot.ai.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.ai.dto.request.AiChatRequest;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.dto.response.AiStreamChunk;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * REST Controller providing production AI copilot endpoints.
 * <p>
 * Supports real-time Server-Sent Events (SSE) token streaming for responsive
 * frontend chat interfaces, as well as synchronous AI completions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Validated
@Tag(name = "AI Service", description = "Endpoints for AI career copilot, resume matching, cold emails, and interview prep")
@SecurityRequirement(name = "Bearer Authentication")
public class AiController {

    private final AiService aiService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream AI chat tokens (SSE)",
            description = "Streams token-by-token AI response via Server-Sent Events (text/event-stream) for real-time ChatGPT/Gemini style UI rendering. Automatically incorporates candidate resume and target job context.")
    public Flux<ServerSentEvent<AiStreamChunk>> streamChat(@Valid @RequestBody AiChatRequest request) {
        String userEmail = getCurrentUserEmail();
        log.info("Incoming streamChat request for user: {}, prompt: {}", userEmail, request.getPrompt());

        return aiService.streamChat(request, userEmail)
                .map(chunk -> ServerSentEvent.<AiStreamChunk>builder()
                        .event(chunk.isCompleted() ? "done" : "message")
                        .data(chunk)
                        .build());
    }

    @PostMapping("/chat")
    @Operation(summary = "Synchronous AI chat completion",
            description = "Executes a non-streaming AI prompt and returns the full markdown response, token usage, and metadata.")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(@Valid @RequestBody AiChatRequest request) {
        String userEmail = getCurrentUserEmail();
        log.info("Incoming synchronous chat request for user: {}", userEmail);

        AiChatResponse response = aiService.chat(request, userEmail);

        ApiResponse<AiChatResponse> apiResponse = ApiResponse.<AiChatResponse>builder()
                .success(true)
                .message("AI completion generated successfully.")
                .data(response)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    @GetMapping("/resume-context")
    @Operation(summary = "Get current candidate resume context",
            description = "Retrieves the parsed structured resume text that the AI uses to ground its responses.")
    public ResponseEntity<ApiResponse<String>> getResumeContext() {
        String userEmail = getCurrentUserEmail();
        String resumeText = aiService.getResumeContext(userEmail);

        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("Candidate resume context retrieved successfully.")
                .data(resumeText)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    @Operation(summary = "Check AI service status and active model",
            description = "Returns AI provider operational status, active model, and configuration details.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        Map<String, Object> healthInfo = Map.of(
                "status", "UP",
                "activeModel", aiService.getActiveModel(),
                "streamingSupported", true,
                "timestamp", LocalDateTime.now()
        );

        ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("AI Service is active and healthy.")
                .data(healthInfo)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Resolves currently authenticated user email from Spring Security Context.
     */
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return "anonymous@copilot.local";
    }
}

package com.developer.copilot.ai.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
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
import com.developer.copilot.common.security.CurrentUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "AI Service", description = "Career-assistance AI: chat, streaming, resume context, and configuration metadata")
@SecurityRequirement(name = "Bearer Authentication")
public class AiController {

    private final AiService aiService;
    private final CurrentUserService currentUserService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream AI chat tokens (SSE)",
            description = "Streams token-by-token AI response via Server-Sent Events (`text/event-stream`). "
                    + "Emits `message` events for tokens, a final `done` event on success (`finishReason=STOP`), "
                    + "or a terminal `error` event on failure (`finishReason=ERROR`). "
                    + "HTTP status may still be 200 when the provider fails mid-stream — clients must treat "
                    + "terminal `error` / `finishReason=ERROR` as failure. "
                    + "Context precedence: customResumeText over resumeId; jobDescription over jobId.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "SSE stream opened. Inspect terminal event: `done` = success, `error` = failure.",
                    content = @Content(
                            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                            schema = @Schema(implementation = AiStreamChunk.class),
                            examples = {
                                    @ExampleObject(
                                            name = "message",
                                            value = "event:message\\ndata:{\"content\":\"Hello\",\"isCompleted\":false,\"model\":\"gemini-flash-latest\"}\\n\\n"),
                                    @ExampleObject(
                                            name = "done",
                                            value = "event:done\\ndata:{\"content\":\"\",\"isCompleted\":true,\"finishReason\":\"STOP\",\"model\":\"gemini-flash-latest\"}\\n\\n"),
                                    @ExampleObject(
                                            name = "error",
                                            value = "event:error\\ndata:{\"content\":\"AI Service Error: ...\",\"isCompleted\":true,\"finishReason\":\"ERROR\",\"model\":\"gemini-flash-latest\"}\\n\\n")
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Referenced job or resume not found for this user"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Resume is still being processed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Resume parsing failed")
    })
    public Flux<ServerSentEvent<AiStreamChunk>> streamChat(@Valid @RequestBody AiChatRequest request) {
        String userEmail = currentUserService.getCurrentUser().getEmail();
        int promptLength = request.getPrompt() != null ? request.getPrompt().length() : 0;
        log.info("Incoming streamChat request for user={}, mode={}, promptLength={}",
                userEmail, request.getMode(), promptLength);

        return aiService.streamChat(request, userEmail)
                .map(chunk -> ServerSentEvent.<AiStreamChunk>builder()
                        .event(resolveSseEventName(chunk))
                        .data(chunk)
                        .build());
    }

    @PostMapping("/chat")
    @Operation(
            summary = "Synchronous AI chat completion",
            description = "Returns one complete career-assistance response using optional owned resume/job context. "
                    + "Context precedence: customResumeText over resumeId; jobDescription over jobId.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "AI completion generated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Referenced job or resume not found for this user"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Resume is still being processed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Resume parsing failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Upstream AI provider failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(@Valid @RequestBody AiChatRequest request) {
        String userEmail = currentUserService.getCurrentUser().getEmail();
        int promptLength = request.getPrompt() != null ? request.getPrompt().length() : 0;
        log.info("Incoming synchronous chat request for user={}, mode={}, promptLength={}",
                userEmail, request.getMode(), promptLength);

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
    @Operation(
            summary = "Get current candidate resume context",
            description = "Returns the authenticated user's completed, non-empty high-priority parsed resume context "
                    + "used to ground AI responses.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resume context returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Resume or profile not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Resume is still being processed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Resume parsing failed")
    })
    public ResponseEntity<ApiResponse<String>> getResumeContext() {
        String userEmail = currentUserService.getCurrentUser().getEmail();
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
    @Operation(
            summary = "AI configuration metadata",
            description = "Returns configured AI provider/model metadata. This is not a live provider readiness probe "
                    + "and does not verify API keys or network connectivity.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Configuration metadata returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        Map<String, Object> healthInfo = Map.of(
                "status", "UP",
                "healthCheckType", "configuration",
                "activeModel", aiService.getActiveModel(),
                "streamingSupported", true,
                "timestamp", LocalDateTime.now()
        );

        ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("AI configuration metadata (does not verify provider connectivity).")
                .data(healthInfo)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    private static String resolveSseEventName(AiStreamChunk chunk) {
        if (!chunk.isCompleted()) {
            return "message";
        }
        if ("ERROR".equalsIgnoreCase(chunk.getFinishReason())) {
            return "error";
        }
        return "done";
    }
}

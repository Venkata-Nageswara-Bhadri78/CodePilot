package com.developer.copilot.ai.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.ai.dto.request.AiChatRequest;
import com.developer.copilot.ai.dto.response.AiChatResponse;
import com.developer.copilot.ai.dto.response.AiStreamChunk;
import com.developer.copilot.ai.service.AiService;
import com.developer.copilot.auth.entity.User;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Service", description = """
        Career-assistance AI. 1) Authorize with JWT from POST /api/v1/auth/login \
        (enabled + email-verified; otherwise 401 Unauthorized.). \
        2) Chat works without a stored resume (empty context) unless you send resumeId. \
        GET /resume-context still 404s if there is no high-priority completed parse. \
        3) POST /chat = one JSON answer. POST /chat/stream = SSE — watch terminal event done vs error; \
        HTTP 200 is not enough. 4) customResumeText / jobDescription override ids. \
        5) Extract Job Info is POST /api/v1/job-extraction/parse. Job history chat is chat-assistant. \
        6) GET /health and GET /config are configuration metadata, not a Gemini probe. JWT required. \
        No X-Internal-Api-Key on these URLs.""")
@SecurityRequirement(name = "Bearer Authentication")
public class AiController {

    private static final String CHAT_DESCRIPTION = """
            Returns one complete career-assistance response.

            | Request field | What happens |
            |---|---|
            | customResumeText | Trimmed, sent as resume. No DB. Skips parse |
            | resumeId | Your active resume only. Ignored if custom text present. Missing/foreign → 404 |
            | (neither) | High-priority resume if present; otherwise chat continues with empty resume context |
            | jobDescription | Trimmed JD. Skips jobs table |
            | jobId | Your row: description else original_description. Ignored if JD present. Foreign → 404 |
            | (neither job) | Chat without JD section |
            | mode | Extra system instructions only |
            | temperature | Per-call OpenAI-compat option (0.0–2.0) |

            data.model is the configured app.ai.default-model. Token fields may be null.
            Pending parse is 409. Failed or empty parse is 422. Provider failure is 502. Rate limit is 429.""";

    private static final String STREAM_DESCRIPTION = """
            Streams token-by-token AI response via Server-Sent Events (`text/event-stream`).
            Same body and grounding rules as POST /chat.
            Emits `message` events for tokens, a final `done` event on success (`finishReason=STOP`),
            or a terminal `error` event on failure (`finishReason=ERROR`).
            HTTP status may still be 200 when the provider fails mid-stream — clients must treat
            terminal `error` / `finishReason=ERROR` as failure.
            Swagger Try-it-out often buffers SSE; use curl -N instead:
            `curl -N -H "Authorization: Bearer …" -H "Content-Type: application/json" -H "Accept: text/event-stream" -d '{"prompt":"Hi"}' http://localhost:8080/api/v1/ai/chat/stream`""";

    private final AiService aiService;
    private final CurrentUserService currentUserService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream AI chat tokens (SSE)",
            description = STREAM_DESCRIPTION)
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Referenced job or explicit resumeId not found for this user"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Resume is still being processed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Resume parsing failed or extracted text was empty"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Chat rate limit exceeded; see Retry-After"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "AI circuit open or bulkhead full")
    })
    public Flux<ServerSentEvent<AiStreamChunk>> streamChat(@Valid @RequestBody AiChatRequest request) {
        User user = currentUserService.getCurrentUser();
        int promptLength = request.getPrompt() != null ? request.getPrompt().length() : 0;
        log.info("Incoming streamChat request for userId={}, mode={}, promptLength={}",
                user.getId(), request.getMode(), promptLength);

        return aiService.streamChat(request, user.getId())
                .map(chunk -> ServerSentEvent.<AiStreamChunk>builder()
                        .event(resolveSseEventName(chunk))
                        .data(chunk)
                        .build());
    }

    @PostMapping("/chat")
    @Operation(
            summary = "Synchronous AI chat completion",
            description = CHAT_DESCRIPTION)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "AI completion generated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Referenced job or explicit resumeId not found for this user"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Resume is still being processed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Resume parsing failed or extracted text was empty"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Chat rate limit exceeded; see Retry-After"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Upstream AI provider failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "AI circuit open or bulkhead full"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(@Valid @RequestBody AiChatRequest request) {
        User user = currentUserService.getCurrentUser();
        int promptLength = request.getPrompt() != null ? request.getPrompt().length() : 0;
        log.info("Incoming synchronous chat request for userId={}, mode={}, promptLength={}",
                user.getId(), request.getMode(), promptLength);

        AiChatResponse response = aiService.chat(request, user.getId());

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
            description = "Returns the authenticated user's completed, non-empty high-priority parsed resume "
                    + "as plain text (PII). No query id. 404 if none. 409 if still processing. 422 if parse failed or empty.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Resume context returned as plain text",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    name = "context",
                                    value = "{\"success\":true,\"message\":\"Candidate resume context retrieved successfully.\","
                                            + "\"data\":\"NAME: Jane Doe\\nEMAIL: jane@example.com\",\"timestamp\":\"2026-01-01T12:00:00\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Resume or profile not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Resume is still being processed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Resume parsing failed or extracted text was empty"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Resume-context rate limit exceeded; see Retry-After")
    })
    public ResponseEntity<ApiResponse<String>> getResumeContext() {
        User user = currentUserService.getCurrentUser();
        log.info("Incoming resume-context request for userId={}", user.getId());
        String resumeText = aiService.getResumeContext();

        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("Candidate resume context retrieved successfully.")
                .data(resumeText)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping({"/health", "/config"})
    @Operation(
            summary = "AI configuration metadata",
            description = "Returns configured AI provider/model metadata. This is not a live provider readiness probe "
                    + "and does not verify API keys or network connectivity. Do not use as Kubernetes readiness. "
                    + "GET /config is the same payload as GET /health.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Configuration metadata returned (status=UP always means Spring is up, not Gemini)"),
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

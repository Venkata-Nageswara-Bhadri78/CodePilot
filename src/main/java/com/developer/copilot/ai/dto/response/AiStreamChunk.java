package com.developer.copilot.ai.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Server-Sent Events (SSE) streaming token chunk payload")
public class AiStreamChunk {

    @Schema(description = "Incremental content token emitted by the LLM stream", example = "Hello")
    private String content;

    @Schema(description = "Indicates whether the stream has completed", example = "false")
    private boolean isCompleted;

    @Schema(description = "Finish reason when stream finishes: STOP on success, ERROR on failure", example = "STOP")
    private String finishReason;

    @Schema(description = "LLM model used", example = "gemini-flash-latest")
    private String model;

    @Schema(description = "Timestamp of the emitted chunk")
    private LocalDateTime timestamp;
}

package com.developer.copilot.ai.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal request passed from the {@code chatassistant} module to {@code AiService} to
 * continue a multi-turn conversation grounded in one specific job. Not exposed as a public
 * HTTP endpoint — this is an in-process service contract.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Internal request to continue a job-scoped multi-turn AI chat")
public class JobChatAiRequest {

    @NotNull(message = "Job ID cannot be null.")
    @Schema(description = "The job this conversation is grounded in", example = "123")
    private Long jobId;

    @Schema(description = "Optional Resume ID owned by the user")
    private Long resumeId;

    @Size(max = 50000, message = "Custom resume text cannot exceed 50000 characters.")
    @Schema(description = "Optional custom resume text overriding stored resume context", maxLength = 50000)
    private String customResumeText;

    @Valid
    @Size(max = 40, message = "Prior turns cannot exceed 40 entries.")
    @Schema(description = "Prior turns of this conversation, oldest first (max 40)")
    private List<ChatTurnDto> priorTurns;

    @NotBlank(message = "New prompt cannot be blank.")
    @Size(max = 8000, message = "New prompt cannot exceed 8000 characters.")
    @Schema(description = "The new user prompt to answer", maxLength = 8000)
    private String newPrompt;
}

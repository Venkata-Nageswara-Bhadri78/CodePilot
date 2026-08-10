package com.developer.copilot.ai.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal request passed from the {@code chatassistant} module to {@code AiService} to
 * continue a multi-turn conversation grounded in one specific job. Not exposed directly as
 * a public controller endpoint - this is an in-process, service-to-service contract, the
 * same pattern used for {@link JobExtractionAiRequest}.
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

    @Schema(description = "Optional custom resume text to override the default stored user resume context")
    private String customResumeText;

    @Schema(description = "Prior turns of this conversation, oldest first, used to rebuild multi-turn context")
    private List<ChatTurnDto> priorTurns;

    @NotBlank(message = "New prompt cannot be blank.")
    @Schema(description = "The new user prompt to answer, continuing the conversation")
    private String newPrompt;
}

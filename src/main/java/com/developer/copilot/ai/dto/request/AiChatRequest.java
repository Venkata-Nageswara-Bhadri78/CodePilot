package com.developer.copilot.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for AI chat and situational prompts")
public class AiChatRequest {

    @NotBlank(message = "Prompt cannot be blank.")
    @Schema(description = "User situational prompt or question (e.g., 'Generate cold email for hiring manager', 'Rate my resume across JD')",
            example = "Rate my resume against this job description and suggest 3 high-impact improvements.")
    private String prompt;

    @Schema(description = "Job description text or structured JSON context",
            example = "Senior Full Stack Java Engineer required with 3+ years experience in Spring Boot, React, and AWS.")
    private String jobDescription;

    @Schema(description = "Optional Job ID to reference existing saved job in Copilot database",
            example = "1")
    private Long jobId;

    @Schema(description = "Optional Resume ID to reference a specific uploaded resume owned by the user",
            example = "5")
    private Long resumeId;

    @Schema(description = "Optional custom resume text to override the default stored user resume context",
            example = "Venkata Nageswara - Senior Java Full Stack Developer with 4 years experience...")
    private String customResumeText;

    @Builder.Default
    @Schema(description = "Situational mode to guide AI prompt persona and output structure",
            example = "GENERAL_CHAT")
    private AiMode mode = AiMode.GENERAL_CHAT;

    @Schema(description = "Optional temperature override (0.0 to 1.0) for creativity control",
            example = "0.7")
    private Double temperature;
}

package com.developer.copilot.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for AI chat and situational prompts. "
        + "Context precedence: customResumeText overrides resumeId; jobDescription overrides jobId. "
        + "IDs must belong to the authenticated user.")
public class AiChatRequest {

    @NotBlank(message = "Prompt cannot be blank.")
    @Size(max = 8000, message = "Prompt cannot exceed 8000 characters.")
    @Schema(description = "User situational prompt or question",
            example = "Rate my resume against this job description and suggest 3 high-impact improvements.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 8000)
    private String prompt;

    @Size(max = 16000, message = "Job description cannot exceed 16000 characters.")
    @Schema(description = "Inline job description text. When present, overrides jobId.",
            example = "Senior Full Stack Java Engineer required with 3+ years experience in Spring Boot, React, and AWS.",
            maxLength = 16000)
    private String jobDescription;

    @Schema(description = "Saved job ID owned by the authenticated user. Ignored when jobDescription is provided.",
            example = "1")
    private Long jobId;

    @Schema(description = "Resume ID owned by the authenticated user. Ignored when customResumeText is provided.",
            example = "5")
    private Long resumeId;

    @Size(max = 16000, message = "Custom resume text cannot exceed 16000 characters.")
    @Schema(description = "Inline resume text that overrides resumeId and the high-priority resume.",
            example = "Senior Java Full Stack Developer with 4 years experience...",
            maxLength = 16000)
    private String customResumeText;

    @Builder.Default
    @Schema(description = "Situational mode guiding AI persona and output structure. "
            + "GENERAL_CHAT (default), RESUME_REVIEW, COVER_LETTER, COLD_EMAIL, INTERVIEW_PREP, MATCH_ANALYSIS.",
            example = "GENERAL_CHAT")
    private AiMode mode = AiMode.GENERAL_CHAT;

    @DecimalMin(value = "0.0", message = "Temperature must be at least 0.0.")
    @DecimalMax(value = "2.0", message = "Temperature must be at most 2.0.")
    @Schema(description = "Optional creativity control from 0.0 (deterministic) to 2.0. Applied when provided.",
            example = "0.7",
            minimum = "0.0",
            maximum = "2.0")
    private Double temperature;
}

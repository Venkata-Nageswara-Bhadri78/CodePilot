package com.developer.copilot.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal request to score a selected resume against an already-structured job.
 * Not a public HTTP contract.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(hidden = true, description = "Internal request to compute resumeToJobScore only")
public class ResumeToJobScoreAiRequest {

    @Schema(description = "Parsed resume context text. Blank when no usable parse exists.")
    private String resumeContext;

    @Schema(description = "Compact structured snapshot of the saved job (no notes or status).")
    private String jobSnapshot;
}

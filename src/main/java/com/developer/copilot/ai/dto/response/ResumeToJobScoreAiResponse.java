package com.developer.copilot.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured output for resume-switch scoring. The model must return only this field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(hidden = true, description = "Integer-only resume-to-job match score")
public class ResumeToJobScoreAiResponse {

    @JsonPropertyDescription("Integer from 0 to 100 inclusive measuring how well the candidate resume matches "
            + "the structured job. 0 if there is no resume, no overlap, or insufficient information. "
            + "100 is an excellent match. Never a string, never a range, never outside 0-100.")
    private Integer resumeToJobScore;
}

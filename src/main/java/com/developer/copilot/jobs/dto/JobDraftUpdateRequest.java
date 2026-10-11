package com.developer.copilot.jobs.dto;

import com.developer.copilot.jobs.util.ResumeToJobScoreSupport;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Full content replace for a draft. Every field is optional. Null or blank clears that field.
 * Values longer than the jobs limits are clipped. Failure reason is not editable.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Replaces the editable content of a job draft. Null or blank clears a field. "
        + "Omitted JSON fields are null and clear the stored value. Notes and job status are not accepted.")
public class JobDraftUpdateRequest {

    private String sourceUrl;
    private String originalDescription;
    private String description;
    private String title;
    private String company;
    private String location;
    private String employmentType;
    private String workMode;
    private String experience;
    private String salary;
    private String education;
    private String department;
    private String industry;
    private String sourcePlatform;
    private String skills;

    @Schema(description = "Resume id to keep with the draft. Checked for ownership when the draft is saved as a job.",
            example = "12")
    private Long resume;

    @Schema(description = "Resume-to-job score, 0–100. Null clears it.", example = "78")
    @Min(value = ResumeToJobScoreSupport.MIN_SCORE, message = "resumeToJobScore must be between 0 and 100.")
    @Max(value = ResumeToJobScoreSupport.MAX_SCORE, message = "resumeToJobScore must be between 0 and 100.")
    private Integer resumeToJobScore;
}

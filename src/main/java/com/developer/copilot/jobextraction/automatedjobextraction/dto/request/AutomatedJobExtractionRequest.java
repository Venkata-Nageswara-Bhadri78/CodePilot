package com.developer.copilot.jobextraction.automatedjobextraction.dto.request;

import com.developer.copilot.jobextraction.automatedjobextraction.util.AutomatedJobExtractionLimits;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Inbound request for {@code POST /api/v1/automated-job-extraction/parse}: only the
 * job posting URL. Page access and job-specific extraction happen server-side.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Job posting URL submitted for automated extraction. "
        + "Must be an absolute http/https URL (enforced after Bean Validation).")
public class AutomatedJobExtractionRequest {

    @NotBlank(message = "Job URL cannot be blank.")
    @Size(max = AutomatedJobExtractionLimits.MAX_URL_LENGTH,
            message = "Job URL cannot exceed " + AutomatedJobExtractionLimits.MAX_URL_LENGTH + " characters.")
    @Schema(description = "The job posting URL exactly as copied by the user (may include tracking params). "
                    + "Not a @Pattern — javascript: and missing hosts fail later with 400.",
            example = "https://company.com/careers/software-engineer-12345",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = AutomatedJobExtractionLimits.MAX_URL_LENGTH)
    private String sourceUrl;
}

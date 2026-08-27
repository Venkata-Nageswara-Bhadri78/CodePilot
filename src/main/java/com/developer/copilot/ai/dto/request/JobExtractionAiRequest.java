package com.developer.copilot.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal request passed from the {@code jobextraction} module to {@code AiService}.
 * Not exposed as a public HTTP endpoint — this is an in-process service contract.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Internal request to extract structured job information from pasted job posting text")
public class JobExtractionAiRequest {

    @NotBlank(message = "Job URL cannot be blank.")
    @Size(max = 2048, message = "Job URL cannot exceed 2048 characters.")
    @Schema(description = "Canonicalized source URL of the job posting",
            example = "https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/Program-Manager-Sr-Consultant_REF087194W",
            maxLength = 2048)
    private String jobUrl;

    @NotBlank(message = "Raw job text cannot be blank.")
    @Size(max = 100000, message = "Raw job text cannot exceed 100000 characters.")
    @Schema(description = "Full raw text pasted from the job posting page", maxLength = 100000)
    private String rawJobText;
}
package com.developer.copilot.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal request passed from the {@code jobextraction} module to {@code AiService} to
 * parse a pasted job posting into strict structured JSON. Not exposed directly as a public
 * controller endpoint - this is an in-process, service-to-service contract.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Internal request to extract structured job information from pasted job posting text")
public class JobExtractionAiRequest {

    @NotBlank(message = "Job URL cannot be blank.")
    @Schema(description = "Canonicalized source URL of the job posting",
            example = "https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/Program-Manager-Sr-Consultant_REF087194W")
    private String jobUrl;

    @NotBlank(message = "Raw job text cannot be blank.")
    @Schema(description = "Full raw text the user pasted from the job posting page (may contain unrelated page noise)")
    private String rawJobText;
}

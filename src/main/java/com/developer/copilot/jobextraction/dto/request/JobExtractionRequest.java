package com.developer.copilot.jobextraction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Inbound request for {@code POST /api/v1/job-extraction/parse}: the raw URL and pasted job
 * posting text a user provides from the "Extract Job Info" frontend flow.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Raw job URL and pasted job posting text submitted for AI extraction")
public class JobExtractionRequest {

    @NotBlank(message = "Job URL cannot be blank.")
    @Size(max = 2000, message = "Job URL cannot exceed 2000 characters.")
    @Schema(description = "The job posting URL exactly as copied by the user (may include tracking/query params)",
            example = "https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/Program-Manager-Sr-Consultant_REF087194W?share_id=LinkedIn_corporate_page")
    private String sourceUrl;

    @NotBlank(message = "Pasted job text cannot be blank.")
    @Size(max = 50000, message = "Pasted job text cannot exceed 50000 characters.")
    @Schema(description = "The entire job posting page content pasted by the user (Ctrl+A / Ctrl+C / Ctrl+V) - "
            + "may contain unrelated page noise which the AI extraction step will ignore",
            example = "Program Manager, Sr. Consultant ... Full job description ...")
    private String rawJobText;
}

package com.developer.copilot.jobextraction.dto.response;

import java.util.List;

import com.developer.copilot.jobextraction.util.JobExtractionLimits;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Preview result returned by {@code POST /api/v1/job-extraction/parse}. Field shape
 * intentionally mirrors {@code com.developer.copilot.jobs.dto.JobRequest} exactly, so the
 * frontend can bind this response directly into an editable review form and submit it,
 * unchanged in structure, to the existing {@code POST /api/v1/jobs} endpoint to persist it.
 * <p>
 * Nothing in this response is persisted yet - the user must review/edit and explicitly
 * click "Add Record" (which calls {@code POST /api/v1/jobs}) to save it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "AI-extracted job fields, clipped to JobRequest sizes, ready for review before "
        + "saving via POST /api/v1/jobs. skills is always an array. requiresManualReview is computed "
        + "by this service (blank or truncated title/company), not by the model.")
public class JobExtractionResultResponse {

    @Schema(description = "Canonicalized source URL (tracking parameters stripped, deterministic form). "
            + "Use this value on POST /api/v1/jobs, not the raw request URL.",
            example = "https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/Program-Manager-Sr-Consultant_REF087194W")
    private String sourceUrl;

    @Schema(description = "The full raw text the user pasted, echoed back verbatim for the review screen")
    private String originalDescription;

    @Schema(description = "AI-cleaned summary of the role. Empty string if the posting had no coherent description.")
    private String description;

    @Schema(description = "Extracted job title, empty string if not found. Max " + JobExtractionLimits.MAX_TITLE_LENGTH
            + " after clip.",
            example = "Program Manager, Sr. Consultant")
    private String title;

    @Schema(description = "Extracted company name, empty string if not found. Max "
            + JobExtractionLimits.MAX_COMPANY_LENGTH + " after clip.",
            example = "Visa")
    private String company;

    @Schema(description = "Extracted location, empty string if not found in the posting")
    private String location;

    @Schema(description = "Extracted employment type, empty string if not found in the posting")
    private String employmentType;

    @Schema(description = "Extracted work mode, empty string if not found in the posting")
    private String workMode;

    @Schema(description = "Extracted experience requirement, empty string if not found in the posting")
    private String experience;

    @Schema(description = "Extracted salary range, empty string if not found in the posting")
    private String salary;

    @Schema(description = "Extracted education requirement, empty string if not found in the posting")
    private String education;

    @Schema(description = "Extracted department/team, empty string if not found in the posting")
    private String department;

    @Schema(description = "Extracted industry, empty string if not found. Never inferred from the company name.")
    private String industry;

    @Schema(description = "Extracted source platform only when the paste names it. Empty string otherwise.")
    private String sourcePlatform;

    @ArraySchema(
            arraySchema = @Schema(description = "Extracted skills, never null. Capped at "
                    + JobExtractionLimits.MAX_SKILL_COUNT + " items, each "
                    + JobExtractionLimits.MAX_SKILL_LENGTH + " chars."),
            schema = @Schema(example = "Java", maxLength = JobExtractionLimits.MAX_SKILL_LENGTH))
    private List<String> skills;

    @Schema(description = "True when title or company is blank, or was truncated to fit jobs @Size. "
            + "Highlight those fields before POST /api/v1/jobs. Omit this flag on save.")
    private boolean requiresManualReview;
}

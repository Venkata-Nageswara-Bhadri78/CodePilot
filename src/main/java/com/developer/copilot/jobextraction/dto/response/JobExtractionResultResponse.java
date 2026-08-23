package com.developer.copilot.jobextraction.dto.response;

import java.util.List;

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
@Schema(description = "AI-extracted job fields, ready for user review before saving via POST /api/v1/jobs")
public class JobExtractionResultResponse {

    @Schema(description = "Canonicalized source URL (tracking parameters stripped, deterministic form)")
    private String sourceUrl;

    @Schema(description = "The full raw text the user pasted, echoed back verbatim for the review screen")
    private String originalDescription;

    @Schema(description = "AI-cleaned summary of the role, derived only from the pasted text")
    private String description;

    @Schema(description = "Extracted job title, empty string if not found in the posting")
    private String title;

    @Schema(description = "Extracted company name, empty string if not found in the posting")
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

    @Schema(description = "Extracted industry, empty string if not found in the posting")
    private String industry;

    @Schema(description = "Extracted source platform (e.g. LinkedIn, Naukri), empty string if not determinable")
    private String sourcePlatform;

    @Schema(description = "Extracted list of required/preferred skills, empty list if none found")
    private List<String> skills;

    @Schema(description = "True when the AI could not confidently extract the required title and/or company "
            + "fields - the frontend should highlight these for manual entry before the user can save via "
            + "POST /api/v1/jobs (which requires both to be non-blank)")
    private boolean requiresManualReview;
}

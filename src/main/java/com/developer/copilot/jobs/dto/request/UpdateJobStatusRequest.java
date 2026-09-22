package com.developer.copilot.jobs.dto.request;

import com.developer.copilot.jobs.entity.JobStatus;
import com.developer.copilot.jobs.util.JobLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Sets the manual application status. When jobStatus is CUSTOM, customStatus is required. "
        + "For any other status, customStatus is ignored and cleared.")
public class UpdateJobStatusRequest {

    @NotNull(message = "Job status is required.")
    @Schema(example = "INTERVIEWS",
            allowableValues = {
                    "APPLIED", "SHORTLISTED", "RECEIVED_CALL", "SCREENING_CALL",
                    "INTERVIEWS", "FINAL_ROUND", "SELECTED", "OFFER", "ACCEPTED",
                    "REJECTED", "WITHDRAWN", "ON_HOLD", "CUSTOM"
            })
    private JobStatus jobStatus;

    @Size(max = JobLimits.MAX_CUSTOM_STATUS_LENGTH,
            message = "Custom status cannot exceed " + JobLimits.MAX_CUSTOM_STATUS_LENGTH + " characters.")
    @Schema(description = "Required when jobStatus is CUSTOM. Ignored for predefined statuses.",
            example = "Waiting on compensation discussion")
    private String customStatus;
}

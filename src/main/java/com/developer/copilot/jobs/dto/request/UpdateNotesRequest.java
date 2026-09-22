package com.developer.copilot.jobs.dto.request;

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
@Schema(description = "Replaces job notes. Empty string clears notes. Notes are never sent to AI scoring.")
public class UpdateNotesRequest {

    @NotNull(message = "Notes is required.")
    @Size(max = JobLimits.MAX_NOTES_LENGTH,
            message = "Notes cannot exceed " + JobLimits.MAX_NOTES_LENGTH + " characters.")
    @Schema(example = "Recruiter asked for a take-home this Friday.")
    private String notes;
}

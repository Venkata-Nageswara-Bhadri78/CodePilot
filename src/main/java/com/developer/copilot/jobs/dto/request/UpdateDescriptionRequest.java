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
@Schema(description = "Updates the cleaned description. Empty string clears the field.")
public class UpdateDescriptionRequest {

    @NotNull(message = "Description is required.")
    @Size(max = JobLimits.MAX_DESCRIPTION_LENGTH,
            message = "Description cannot exceed " + JobLimits.MAX_DESCRIPTION_LENGTH + " characters.")
    private String description;
}

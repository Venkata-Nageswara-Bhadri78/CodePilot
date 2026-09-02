package com.developer.copilot.jobs.dto.request;

import com.developer.copilot.jobs.util.JobLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Replaces the original pasted posting text. Cannot be blank.")
public class UpdateOriginalDescriptionRequest {

    @NotBlank(message = "Original description cannot be blank.")
    @Size(max = JobLimits.MAX_DESCRIPTION_LENGTH,
            message = "Original description cannot exceed " + JobLimits.MAX_DESCRIPTION_LENGTH + " characters.")
    private String originalDescription;
}

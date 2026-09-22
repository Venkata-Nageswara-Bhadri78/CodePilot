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
@Schema(description = "Replaces the full comma-separated skills value. Send an empty string to clear skills.")
public class UpdateSkillsRequest {

    @NotNull(message = "Skills is required.")
    @Size(max = JobLimits.MAX_SKILLS_LENGTH,
            message = "Skills cannot exceed " + JobLimits.MAX_SKILLS_LENGTH + " characters.")
    @Schema(example = "Java, Spring Boot, AWS")
    private String skills;
}

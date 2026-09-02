package com.developer.copilot.jobs.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Updates experience. Empty string clears the field.")
public class UpdateExperienceRequest {

    @NotNull(message = "Experience is required.")
    @Size(max = 100, message = "Experience cannot exceed 100 characters.")
    @Schema(example = "2-4 years")
    private String experience;
}

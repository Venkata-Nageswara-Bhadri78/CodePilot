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
@Schema(description = "Updates work mode. Empty string clears the field.")
public class UpdateWorkModeRequest {

    @NotNull(message = "Work mode is required.")
    @Size(max = 50, message = "Work mode cannot exceed 50 characters.")
    @Schema(example = "Hybrid")
    private String workMode;
}

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
@Schema(description = "Updates employment type. Empty string clears the field.")
public class UpdateEmploymentTypeRequest {

    @NotNull(message = "Employment type is required.")
    @Size(max = 100, message = "Employment type cannot exceed 100 characters.")
    @Schema(example = "Full Time")
    private String employmentType;
}

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
@Schema(description = "Updates department. Empty string clears the field.")
public class UpdateDepartmentRequest {

    @NotNull(message = "Department is required.")
    @Size(max = 100, message = "Department cannot exceed 100 characters.")
    @Schema(example = "Engineering")
    private String department;
}

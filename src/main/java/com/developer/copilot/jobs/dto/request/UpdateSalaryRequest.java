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
@Schema(description = "Updates salary as free text. Empty string clears the field. Not a numeric amount.")
public class UpdateSalaryRequest {

    @NotNull(message = "Salary is required.")
    @Size(max = 100, message = "Salary cannot exceed 100 characters.")
    @Schema(example = "15-20 LPA")
    private String salary;
}

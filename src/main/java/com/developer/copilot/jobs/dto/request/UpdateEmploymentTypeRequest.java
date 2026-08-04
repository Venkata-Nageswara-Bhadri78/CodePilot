package com.developer.copilot.jobs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEmploymentTypeRequest {

    @NotBlank(message = "Employment type cannot be blank.")
    @Size(max = 100, message = "Employment type cannot exceed 100 characters.")
    private String employmentType;
}

package com.developer.copilot.jobs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDepartmentRequest {

    @NotBlank(message = "Department cannot be blank.")
    @Size(max = 100, message = "Department cannot exceed 100 characters.")
    private String department;
}

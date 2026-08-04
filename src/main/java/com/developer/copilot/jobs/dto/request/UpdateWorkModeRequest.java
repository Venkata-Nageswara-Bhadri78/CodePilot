package com.developer.copilot.jobs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateWorkModeRequest {

    @NotBlank(message = "Work mode cannot be blank.")
    @Size(max = 50, message = "Work mode cannot exceed 50 characters.")
    private String workMode;
}

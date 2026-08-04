package com.developer.copilot.jobs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEducationRequest {

    @NotBlank(message = "Education cannot be blank.")
    @Size(max = 255, message = "Education cannot exceed 255 characters.")
    private String education;
}

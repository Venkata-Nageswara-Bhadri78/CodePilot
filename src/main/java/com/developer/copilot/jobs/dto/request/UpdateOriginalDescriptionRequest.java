package com.developer.copilot.jobs.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateOriginalDescriptionRequest {

    @NotBlank(message = "Original description cannot be blank.")
    private String originalDescription;
}

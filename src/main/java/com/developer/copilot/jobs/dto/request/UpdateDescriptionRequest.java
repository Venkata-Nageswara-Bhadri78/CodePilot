package com.developer.copilot.jobs.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDescriptionRequest {

    @NotBlank(message = "Description cannot be blank.")
    private String description;
}

package com.developer.copilot.jobs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSourcePlatformRequest {

    @NotBlank(message = "Source platform cannot be blank.")
    @Size(max = 50, message = "Source platform cannot exceed 50 characters.")
    private String sourcePlatform;
}

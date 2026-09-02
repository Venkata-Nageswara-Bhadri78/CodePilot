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
@Schema(description = "Updates source platform. Empty string clears the field.")
public class UpdateSourcePlatformRequest {

    @NotNull(message = "Source platform is required.")
    @Size(max = 50, message = "Source platform cannot exceed 50 characters.")
    @Schema(example = "LinkedIn")
    private String sourcePlatform;
}

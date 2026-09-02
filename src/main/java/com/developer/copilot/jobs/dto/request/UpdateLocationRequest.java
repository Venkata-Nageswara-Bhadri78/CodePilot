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
@Schema(description = "Updates location. Empty string clears the field.")
public class UpdateLocationRequest {

    @NotNull(message = "Location is required.")
    @Size(max = 255, message = "Location cannot exceed 255 characters.")
    @Schema(example = "Hyderabad, India")
    private String location;
}

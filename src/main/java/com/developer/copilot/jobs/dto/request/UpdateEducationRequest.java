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
@Schema(description = "Updates education. Empty string clears the field.")
public class UpdateEducationRequest {

    @NotNull(message = "Education is required.")
    @Size(max = 255, message = "Education cannot exceed 255 characters.")
    @Schema(example = "B.Tech")
    private String education;
}

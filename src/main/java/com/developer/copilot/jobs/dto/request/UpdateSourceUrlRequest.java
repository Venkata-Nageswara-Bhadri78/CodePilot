package com.developer.copilot.jobs.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Replaces the source URL. Must be absolute http/https; tracking params are stripped.")
public class UpdateSourceUrlRequest {

    @NotBlank(message = "Source URL cannot be blank.")
    @Size(max = 2000, message = "Source URL cannot exceed 2000 characters.")
    @Schema(example = "https://www.linkedin.com/jobs/view/1234?utm_source=linkedin")
    private String sourceUrl;
}

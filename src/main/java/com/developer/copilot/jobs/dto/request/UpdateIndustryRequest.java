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
@Schema(description = "Updates industry. Empty string clears the field.")
public class UpdateIndustryRequest {

    @NotNull(message = "Industry is required.")
    @Size(max = 100, message = "Industry cannot exceed 100 characters.")
    @Schema(example = "Information Technology")
    private String industry;
}

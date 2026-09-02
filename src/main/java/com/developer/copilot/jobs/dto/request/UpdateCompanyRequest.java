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
@Schema(description = "Replaces the company name. Cannot be blank.")
public class UpdateCompanyRequest {

    @NotBlank(message = "Company cannot be blank.")
    @Size(max = 255, message = "Company cannot exceed 255 characters.")
    @Schema(example = "Acme Corp")
    private String company;
}

package com.developer.copilot.jobs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateIndustryRequest {

    @NotBlank(message = "Industry cannot be blank.")
    @Size(max = 100, message = "Industry cannot exceed 100 characters.")
    private String industry;
}

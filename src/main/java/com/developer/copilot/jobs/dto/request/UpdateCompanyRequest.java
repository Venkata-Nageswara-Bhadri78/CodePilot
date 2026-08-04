package com.developer.copilot.jobs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCompanyRequest {

    @NotBlank(message = "Company cannot be blank.")
    @Size(max = 255, message = "Company cannot exceed 255 characters.")
    private String company;
}

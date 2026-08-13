package com.developer.copilot.user.dto.experience;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WorkExperienceRequest {

    @NotBlank(message = "Company name is required.")
    @Size(max = 200, message = "Company name must not exceed 200 characters.")
    private String companyName;

    @NotBlank(message = "Job title is required.")
    @Size(max = 200, message = "Job title must not exceed 200 characters.")
    private String jobTitle;

    @NotNull(message = "Start year is required.")
    @Min(value = 1900, message = "Start year must be 1900 or later.")
    @Max(value = 2100, message = "Start year must not exceed 2100.")
    private Integer startYear;

    @Min(value = 1900, message = "End year must be 1900 or later.")
    @Max(value = 2100, message = "End year must not exceed 2100.")
    private Integer endYear;

    @Size(max = 5000, message = "Description must not exceed 5000 characters.")
    private String description;

}

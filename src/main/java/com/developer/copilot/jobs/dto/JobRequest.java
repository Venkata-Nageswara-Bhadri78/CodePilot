package com.developer.copilot.jobs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRequest {

    @NotBlank(message = "Source URL cannot be blank.")
    @Size(max = 2000, message = "Source URL cannot exceed 2000 characters.")
    private String sourceUrl;

    @NotBlank(message = "Original description cannot be blank.")
    private String originalDescription;

    private String description;

    @NotBlank(message = "Title cannot be blank.")
    @Size(max = 255, message = "Title cannot exceed 255 characters.")
    private String title;

    @NotBlank(message = "Company cannot be blank.")
    @Size(max = 255, message = "Company cannot exceed 255 characters.")
    private String company;

    @Size(max = 255, message = "Location cannot exceed 255 characters.")
    private String location;

    @Size(max = 100, message = "Employment type cannot exceed 100 characters.")
    private String employmentType;

    @Size(max = 50, message = "Work mode cannot exceed 50 characters.")
    private String workMode;

    @Size(max = 100, message = "Experience cannot exceed 100 characters.")
    private String experience;

    @Size(max = 100, message = "Salary cannot exceed 100 characters.")
    private String salary;

    @Size(max = 255, message = "Education cannot exceed 255 characters.")
    private String education;

    @Size(max = 100, message = "Department cannot exceed 100 characters.")
    private String department;

    @Size(max = 100, message = "Industry cannot exceed 100 characters.")
    private String industry;

    @Size(max = 50, message = "Source platform cannot exceed 50 characters.")
    private String sourcePlatform;

    private List<String> skills;
}

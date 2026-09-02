package com.developer.copilot.jobs.dto;

import com.developer.copilot.jobs.util.JobLimits;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for creating or fully replacing a saved job posting. "
        + "On PUT, omitted skills are treated as an empty list (full replace).")
public class JobRequest {

    @Schema(description = "The job posting URL. Tracking parameters are stripped on save; "
                    + "only absolute http/https URLs are accepted.",
            example = "https://www.linkedin.com/jobs/view/1234?utm_source=linkedin")
    @NotBlank(message = "Source URL cannot be blank.")
    @Size(max = 2000, message = "Source URL cannot exceed 2000 characters.")
    private String sourceUrl;

    @Schema(description = "Raw job posting text pasted by the user",
            example = "We are hiring a Software Engineer...")
    @NotBlank(message = "Original description cannot be blank.")
    @Size(max = JobLimits.MAX_DESCRIPTION_LENGTH,
            message = "Original description cannot exceed " + JobLimits.MAX_DESCRIPTION_LENGTH + " characters.")
    private String originalDescription;

    @Schema(description = "Cleaned or extracted job description")
    @Size(max = JobLimits.MAX_DESCRIPTION_LENGTH,
            message = "Description cannot exceed " + JobLimits.MAX_DESCRIPTION_LENGTH + " characters.")
    private String description;

    @Schema(description = "Job title", example = "Software Engineer")
    @NotBlank(message = "Title cannot be blank.")
    @Size(max = 255, message = "Title cannot exceed 255 characters.")
    private String title;

    @Schema(description = "Company name", example = "Acme Corp")
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

    @ArraySchema(
            arraySchema = @Schema(description = "Required skills for the role. On PUT, omitting this field clears skills."),
            schema = @Schema(example = "Java", maxLength = 255))
    @Schema(example = "[\"Java\", \"Spring Boot\"]")
    private List<@Size(max = 255, message = "Each skill cannot exceed 255 characters.") String> skills;
}

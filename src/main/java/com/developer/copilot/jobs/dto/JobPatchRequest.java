package com.developer.copilot.jobs.dto;

import com.developer.copilot.jobs.util.JobLimits;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Partial update payload; only provided fields are applied. "
        + "Empty string on optional fields is stored as the cleared state. "
        + "\"skills\": [] clears skills; omitting skills leaves the current list.")
public class JobPatchRequest {

    @Schema(description = "Replacement source URL; must be absolute http/https",
            example = "https://linkedin.com/jobs/view/1234")
    @Size(max = 2000, message = "Source URL cannot exceed 2000 characters.")
    private String sourceUrl;

    @Size(max = JobLimits.MAX_DESCRIPTION_LENGTH,
            message = "Original description cannot exceed " + JobLimits.MAX_DESCRIPTION_LENGTH + " characters.")
    private String originalDescription;

    @Size(max = JobLimits.MAX_DESCRIPTION_LENGTH,
            message = "Description cannot exceed " + JobLimits.MAX_DESCRIPTION_LENGTH + " characters.")
    private String description;

    @Size(max = 255, message = "Title cannot exceed 255 characters.")
    private String title;

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

    @ArraySchema(schema = @Schema(maxLength = 255))
    private List<@Size(max = 255, message = "Each skill cannot exceed 255 characters.") String> skills;
}

package com.developer.copilot.user.dto.experience;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WorkExperienceResponse {

    private Long id;
    private String companyName;
    private String jobTitle;
    private Integer startYear;
    private Integer endYear;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

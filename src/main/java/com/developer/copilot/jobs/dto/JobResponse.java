package com.developer.copilot.jobs.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobResponse {

    private Long id;

    private String sourceUrl;

    private String originalDescription;

    private String description;

    private String title;

    private String company;

    private String location;

    private String employmentType;

    private String workMode;

    private String experience;

    private String salary;

    private String education;

    private String department;

    private String industry;

    private String sourcePlatform;

    private List<String> skills;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

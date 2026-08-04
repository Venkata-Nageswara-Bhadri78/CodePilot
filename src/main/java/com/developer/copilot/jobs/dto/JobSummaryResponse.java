package com.developer.copilot.jobs.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSummaryResponse {

    private Long id;

    private String title;

    private String company;

    private String location;

    private String employmentType;

    private String workMode;

    private String experience;

    private String salary;

    private String sourcePlatform;

    private List<String> skills;

    private LocalDateTime createdAt;
}

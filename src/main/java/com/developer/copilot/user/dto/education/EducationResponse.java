package com.developer.copilot.user.dto.education;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EducationResponse {

    private Long id;
    private String institutionName;
    private String field;
    private Integer startYear;
    private Integer endYear;
    private String scoreOrGrade;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

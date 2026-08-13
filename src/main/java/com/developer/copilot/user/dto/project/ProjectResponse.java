package com.developer.copilot.user.dto.project;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProjectResponse {

    private Long id;
    private String projectTitle;
    private String projectDescription;
    private String projectLink;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

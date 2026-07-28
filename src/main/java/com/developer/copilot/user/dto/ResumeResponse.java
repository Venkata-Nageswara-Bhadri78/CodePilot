package com.developer.copilot.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResumeResponse {

    private Long id;

    private String originalFilename;

    private Long fileSize;

    private Boolean highPriority;

}
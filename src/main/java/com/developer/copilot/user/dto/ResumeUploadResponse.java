package com.developer.copilot.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResumeUploadResponse {

    private Long resumeId;

    private String message;

}
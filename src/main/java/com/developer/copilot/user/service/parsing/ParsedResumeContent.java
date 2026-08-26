package com.developer.copilot.user.service.parsing;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Structured view of a resume produced by {@link ResumeSectionParser}.
 */
@Getter
@Builder
public class ParsedResumeContent {

    /**
     * Detected sections in canonical order. Absent sections are simply not present.
     */
    @Builder.Default
    private final Map<ResumeSection, String> sections = Map.of();

    private final String candidateName;

    private final String email;

    private final String phone;

    private final String location;

    private final String linkedinUrl;

    private final String githubUrl;
}

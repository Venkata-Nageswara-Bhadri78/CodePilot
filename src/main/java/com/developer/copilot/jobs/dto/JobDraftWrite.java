package com.developer.copilot.jobs.dto;

import com.developer.copilot.jobs.entity.JobDraftFailureCategory;

import lombok.Builder;
import lombok.Getter;

/**
 * Server-side payload for inserting or updating a draft. Not an HTTP body.
 */
@Getter
@Builder
public class JobDraftWrite {

    private final String sourceUrl;
    private final String originalDescription;
    private final String description;
    private final String title;
    private final String company;
    private final String location;
    private final String employmentType;
    private final String workMode;
    private final String experience;
    private final String salary;
    private final String education;
    private final String department;
    private final String industry;
    private final String sourcePlatform;
    private final String skills;
    private final Long resume;
    private final Integer resumeToJobScore;
    private final JobDraftFailureCategory failureCategory;
    private final String failureReason;
}

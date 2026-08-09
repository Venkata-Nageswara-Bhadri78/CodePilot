package com.developer.copilot.jobextraction.mapper;

import java.util.ArrayList;
import java.util.Collections;

import org.springframework.stereotype.Component;

import com.developer.copilot.ai.dto.response.JobExtractionAiResponse;
import com.developer.copilot.jobextraction.dto.response.JobExtractionResultResponse;

/**
 * Combines the AI's extracted fields with the two values the backend already knows with
 * certainty (the canonicalized URL and the raw pasted text) into the outward-facing preview
 * response.
 */
@Component
public class JobExtractionMapper {

    public JobExtractionResultResponse toResultResponse(
            JobExtractionAiResponse aiResponse,
            String normalizedSourceUrl,
            String rawJobText
    ) {
        return JobExtractionResultResponse.builder()
                .sourceUrl(normalizedSourceUrl)
                .originalDescription(rawJobText)
                .description(aiResponse.getDescription())
                .title(aiResponse.getTitle())
                .company(aiResponse.getCompany())
                .location(aiResponse.getLocation())
                .employmentType(aiResponse.getEmploymentType())
                .workMode(aiResponse.getWorkMode())
                .experience(aiResponse.getExperience())
                .salary(aiResponse.getSalary())
                .education(aiResponse.getEducation())
                .department(aiResponse.getDepartment())
                .industry(aiResponse.getIndustry())
                .sourcePlatform(aiResponse.getSourcePlatform())
                .skills(aiResponse.getSkills() != null
                        ? new ArrayList<>(aiResponse.getSkills())
                        : Collections.emptyList())
                .build();
    }
}

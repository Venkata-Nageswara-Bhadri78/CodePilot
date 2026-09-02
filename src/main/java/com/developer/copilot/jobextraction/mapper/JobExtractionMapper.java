package com.developer.copilot.jobextraction.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.developer.copilot.ai.dto.response.JobExtractionAiResponse;
import com.developer.copilot.jobextraction.dto.response.JobExtractionResultResponse;
import com.developer.copilot.jobextraction.util.JobExtractionLimits;

/**
 * Combines the AI's extracted fields with the two values the backend already knows with
 * certainty (the canonicalized URL and the raw pasted text) into the outward-facing preview
 * response. AI strings are clipped to {@link JobExtractionLimits} so save via
 * {@code POST /api/v1/jobs} does not fail on {@code @Size}.
 */
@Component
public class JobExtractionMapper {

    public JobExtractionResultResponse toResultResponse(
            JobExtractionAiResponse aiResponse,
            String normalizedSourceUrl,
            String rawJobText
    ) {
        Clip title = clip(aiResponse.getTitle(), JobExtractionLimits.MAX_TITLE_LENGTH);
        Clip company = clip(aiResponse.getCompany(), JobExtractionLimits.MAX_COMPANY_LENGTH);

        return JobExtractionResultResponse.builder()
                .sourceUrl(normalizedSourceUrl)
                .originalDescription(rawJobText)
                .description(clip(aiResponse.getDescription(), JobExtractionLimits.MAX_DESCRIPTION_LENGTH).value())
                .title(title.value())
                .company(company.value())
                .location(clip(aiResponse.getLocation(), JobExtractionLimits.MAX_LOCATION_LENGTH).value())
                .employmentType(clip(aiResponse.getEmploymentType(), JobExtractionLimits.MAX_EMPLOYMENT_TYPE_LENGTH).value())
                .workMode(clip(aiResponse.getWorkMode(), JobExtractionLimits.MAX_WORK_MODE_LENGTH).value())
                .experience(clip(aiResponse.getExperience(), JobExtractionLimits.MAX_EXPERIENCE_LENGTH).value())
                .salary(clip(aiResponse.getSalary(), JobExtractionLimits.MAX_SALARY_LENGTH).value())
                .education(clip(aiResponse.getEducation(), JobExtractionLimits.MAX_EDUCATION_LENGTH).value())
                .department(clip(aiResponse.getDepartment(), JobExtractionLimits.MAX_DEPARTMENT_LENGTH).value())
                .industry(clip(aiResponse.getIndustry(), JobExtractionLimits.MAX_INDUSTRY_LENGTH).value())
                .sourcePlatform(clip(aiResponse.getSourcePlatform(), JobExtractionLimits.MAX_SOURCE_PLATFORM_LENGTH).value())
                .skills(clipSkills(aiResponse.getSkills()))
                .requiresManualReview(isBlank(title.value()) || isBlank(company.value())
                        || title.truncated() || company.truncated())
                .build();
    }

    private List<String> clipSkills(List<String> skills) {
        List<String> clipped = new ArrayList<>();
        if (skills == null) {
            return clipped;
        }
        for (String skill : skills) {
            if (clipped.size() >= JobExtractionLimits.MAX_SKILL_COUNT) {
                break;
            }
            Clip item = clip(skill, JobExtractionLimits.MAX_SKILL_LENGTH);
            if (!isBlank(item.value())) {
                clipped.add(item.value());
            }
        }
        return clipped;
    }

    private Clip clip(String value, int maxLength) {
        if (value == null) {
            return Clip.unchanged(null);
        }
        String cleaned = blankUnsafeUriScheme(stripControls(value));
        if (cleaned.length() <= maxLength) {
            return Clip.unchanged(cleaned);
        }
        return Clip.truncated(cleaned.substring(0, maxLength));
    }

    /**
     * If the whole field is a {@code javascript:} or {@code data:} URI, drop it so a review
     * UI that turns text into {@code href} cannot navigate there. Markup such as
     * {@code <script>} is left as JSON text — clients must render it escaped.
     */
    private String blankUnsafeUriScheme(String value) {
        String trimmed = value.stripLeading();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:")) {
            return "";
        }
        return value;
    }

    /**
     * Drops C0 control characters except tab/newline/carriage-return so extracted fields
     * cannot smuggle log/HTML surprises. Does not strip markup — the API is JSON text.
     */
    private String stripControls(String value) {
        StringBuilder cleaned = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || c >= 32) {
                cleaned.append(c);
            }
        }
        return cleaned.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Clip(String value, boolean truncated) {
        static Clip unchanged(String value) {
            return new Clip(value, false);
        }

        static Clip truncated(String value) {
            return new Clip(value, true);
        }
    }
}

package com.developer.copilot.jobextraction.automatedjobextraction.extract;

import java.util.List;

import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.util.AutomatedJobExtractionLimits;

/**
 * Formats extracted fields as labeled plain text for the existing manual extraction
 * service. Only fields that were actually found are included.
 */
@Component
public class ExtractedJobTextFormatter {

    public String format(ExtractedJobFields fields) {
        if (fields == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        line(sb, "Source URL", fields.getSourceUrl());
        line(sb, "Job Title", fields.getTitle());
        line(sb, "Company", fields.getCompany());
        line(sb, "Location", fields.getLocation());
        line(sb, "Employment Type", fields.getEmploymentType());
        line(sb, "Work Mode", fields.getWorkMode());
        line(sb, "Experience", fields.getExperience());
        line(sb, "Salary", fields.getSalary());
        line(sb, "Department", fields.getDepartment());
        line(sb, "Seniority Level", fields.getSeniorityLevel());
        line(sb, "Job ID", fields.getJobId());
        line(sb, "Posted Date", fields.getPostedDate());
        line(sb, "Application Deadline", fields.getApplicationDeadline());
        line(sb, "Application URL", fields.getApplicationUrl());
        bullets(sb, "Education", fields.getEducation());
        bullets(sb, "Skills", fields.getSkills());
        bullets(sb, "Certifications", fields.getCertifications());
        bullets(sb, "Benefits", fields.getBenefits());
        bullets(sb, "Responsibilities", fields.getResponsibilities());
        bullets(sb, "Requirements", fields.getRequirements());
        bullets(sb, "Preferred Qualifications", fields.getPreferredQualifications());
        if (notBlank(fields.getDescription())) {
            sb.append("Job Description:").append('\n').append(fields.getDescription().trim()).append('\n');
        }
        String text = sb.toString().trim();
        if (text.length() > AutomatedJobExtractionLimits.MAX_EXTRACTED_TEXT_LENGTH) {
            return text.substring(0, AutomatedJobExtractionLimits.MAX_EXTRACTED_TEXT_LENGTH);
        }
        return text;
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (notBlank(value)) {
            sb.append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    private static void bullets(StringBuilder sb, String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        boolean any = false;
        StringBuilder block = new StringBuilder();
        for (String value : values) {
            if (notBlank(value)) {
                any = true;
                block.append("- ").append(value.trim()).append('\n');
            }
        }
        if (any) {
            sb.append(label).append(':').append('\n').append(block);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

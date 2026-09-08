package com.developer.copilot.jobextraction.automatedjobextraction.extract;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Job-specific fields gathered from page signals. Blank/null means the source did not
 * contain that information — never inferred.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class ExtractedJobFields {

    private String title;
    private String company;
    private String location;
    private String employmentType;
    private String workMode;
    private String experience;
    private String salary;
    private String description;
    @Builder.Default
    private List<String> responsibilities = new ArrayList<>();
    @Builder.Default
    private List<String> requirements = new ArrayList<>();
    @Builder.Default
    private List<String> preferredQualifications = new ArrayList<>();
    @Builder.Default
    private List<String> skills = new ArrayList<>();
    @Builder.Default
    private List<String> education = new ArrayList<>();
    @Builder.Default
    private List<String> certifications = new ArrayList<>();
    @Builder.Default
    private List<String> benefits = new ArrayList<>();
    private String applicationDeadline;
    private String postedDate;
    private String applicationUrl;
    private String jobId;
    private String department;
    private String seniorityLevel;
    private String sourceUrl;
    private boolean fromStructuredData;

    public ExtractedJobFields() {
        this.responsibilities = new ArrayList<>();
        this.requirements = new ArrayList<>();
        this.preferredQualifications = new ArrayList<>();
        this.skills = new ArrayList<>();
        this.education = new ArrayList<>();
        this.certifications = new ArrayList<>();
        this.benefits = new ArrayList<>();
    }
}

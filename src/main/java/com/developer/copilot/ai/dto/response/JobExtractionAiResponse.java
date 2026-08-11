package com.developer.copilot.ai.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Strict structured extraction target bound via Spring AI's {@code ChatClient} entity
 * conversion ({@code call().entity(JobExtractionAiResponse.class)}). The
 * {@link JsonPropertyDescription} on every field is surfaced by Spring AI's structured-output
 * converter as part of the format instructions sent to the model, directly implementing the
 * "explain each field's constraints so the model returns exactly what's requested" requirement.
 * <p>
 * Deliberately excludes {@code sourceUrl} and {@code originalDescription}: the backend already
 * knows both values with certainty, so trusting the model to echo them back would only add
 * hallucination risk for zero benefit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Strictly parsed job information extracted from a pasted job posting")
public class JobExtractionAiResponse {

    @JsonPropertyDescription("The exact job title as stated in the posting (e.g. 'Senior Full Stack Engineer'). "
            + "Empty string if no clear title is present.")
    private String title;

    @JsonPropertyDescription("The hiring company's name exactly as stated in the posting. Empty string if not present.")
    private String company;

    @JsonPropertyDescription("The job location(s) as stated, including remote/hybrid city or region if mentioned "
            + "(e.g. 'San Francisco, CA (Remote)'). Empty string if not present.")
    private String location;

    @JsonPropertyDescription("Employment type exactly as implied by the posting, e.g. 'Full Time', 'Part Time', "
            + "'Contract', 'Internship'. Empty string if not present.")
    private String employmentType;

    @JsonPropertyDescription("Work arrangement exactly as implied by the posting, e.g. 'Remote', 'Hybrid', 'On-site'. "
            + "Empty string if not present.")
    private String workMode;

    @JsonPropertyDescription("Required years of experience or seniority range exactly as stated, e.g. '4-7 Years'. "
            + "Empty string if not present.")
    private String experience;

    @JsonPropertyDescription("Compensation/salary range exactly as stated, including currency, e.g. "
            + "'$160,000 - $210,000'. Empty string if not present.")
    private String salary;

    @JsonPropertyDescription("Minimum education requirement exactly as stated, e.g. 'Bachelor's in CS or equivalent'. "
            + "Empty string if not present.")
    private String education;

    @JsonPropertyDescription("Team or department exactly as stated, e.g. 'Core Engineering'. Empty string if not present.")
    private String department;

    @JsonPropertyDescription("Industry or business domain of the hiring company, e.g. 'Financial Technology'. "
            + "Empty string if not present or not clearly inferable.")
    private String industry;

    @JsonPropertyDescription("The platform or site this posting text most likely originated from, e.g. 'LinkedIn', "
            + "'Naukri', 'Company Career Site'. Empty string if not determinable.")
    private String sourcePlatform;

    @JsonPropertyDescription("A concise, cleaned-up plain-text summary of the role and its responsibilities, "
            + "derived only from content present in the pasted text - never invented. Empty string if the pasted "
            + "text has no coherent description to summarize.")
    private String description;

    @JsonPropertyDescription("List of required/preferred technical skills, tools, or technologies explicitly "
            + "mentioned in the posting (e.g. ['React', 'Node.js', 'AWS']). Empty list if none are mentioned.")
    private List<String> skills;
}

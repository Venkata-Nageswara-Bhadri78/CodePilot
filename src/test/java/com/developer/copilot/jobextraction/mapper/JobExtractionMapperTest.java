package com.developer.copilot.jobextraction.mapper;

import com.developer.copilot.ai.dto.response.JobExtractionAiResponse;
import com.developer.copilot.jobextraction.dto.response.JobExtractionResultResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobExtractionMapperTest {

    private final JobExtractionMapper mapper = new JobExtractionMapper();

    @Test
    void toResultResponse_MapsAllAiFields() {
        JobExtractionAiResponse aiResponse = JobExtractionAiResponse.builder()
                .title("Senior Engineer")
                .company("Acme")
                .location("Remote")
                .employmentType("Full Time")
                .workMode("Remote")
                .experience("5+ years")
                .salary("100k")
                .education("Bachelors")
                .department("Engineering")
                .industry("Tech")
                .sourcePlatform("LinkedIn")
                .description("Great role")
                .skills(List.of("Java", "Spring"))
                .build();

        JobExtractionResultResponse result =
                mapper.toResultResponse(aiResponse, "https://acme.com/jobs/1", "raw pasted text");

        assertEquals("https://acme.com/jobs/1", result.getSourceUrl());
        assertEquals("raw pasted text", result.getOriginalDescription());
        assertEquals("Senior Engineer", result.getTitle());
        assertEquals("Acme", result.getCompany());
        assertEquals("Remote", result.getLocation());
        assertEquals("Full Time", result.getEmploymentType());
        assertEquals("Remote", result.getWorkMode());
        assertEquals("5+ years", result.getExperience());
        assertEquals("100k", result.getSalary());
        assertEquals("Bachelors", result.getEducation());
        assertEquals("Engineering", result.getDepartment());
        assertEquals("Tech", result.getIndustry());
        assertEquals("LinkedIn", result.getSourcePlatform());
        assertEquals("Great role", result.getDescription());
        assertEquals(List.of("Java", "Spring"), result.getSkills());
        assertFalse(result.isRequiresManualReview());
    }

    @Test
    void toResultResponse_ClipsTitleTo255AndFlagsManualReview() {
        JobExtractionAiResponse aiResponse = JobExtractionAiResponse.builder()
                .title("A".repeat(300))
                .company("Acme")
                .build();

        JobExtractionResultResponse result =
                mapper.toResultResponse(aiResponse, "https://acme.com/jobs/1", "raw text");

        assertEquals(255, result.getTitle().length());
        assertTrue(result.isRequiresManualReview());
    }

    @Test
    void toResultResponse_ClipsWorkModeTo50() {
        JobExtractionAiResponse aiResponse = JobExtractionAiResponse.builder()
                .title("Engineer")
                .company("Acme")
                .workMode("W".repeat(51))
                .build();

        JobExtractionResultResponse result =
                mapper.toResultResponse(aiResponse, "https://acme.com/jobs/1", "raw text");

        assertEquals(50, result.getWorkMode().length());
        assertFalse(result.isRequiresManualReview());
    }

    @Test
    void toResultResponse_ClipsSkillTo255AndCapsCountAt50() {
        java.util.List<String> many = new java.util.ArrayList<>();
        many.add("S".repeat(256));
        for (int i = 0; i < 60; i++) {
            many.add("skill-" + i);
        }
        JobExtractionAiResponse aiResponse = JobExtractionAiResponse.builder()
                .title("Engineer")
                .company("Acme")
                .skills(many)
                .build();

        JobExtractionResultResponse result =
                mapper.toResultResponse(aiResponse, "https://acme.com/jobs/1", "raw text");

        assertEquals(50, result.getSkills().size());
        assertEquals(255, result.getSkills().get(0).length());
    }

    @Test
    void toResultResponse_StripsControlCharsFromTitle() {
        JobExtractionAiResponse aiResponse = JobExtractionAiResponse.builder()
                .title("Eng\u0001ineer")
                .company("Acme")
                .build();

        JobExtractionResultResponse result =
                mapper.toResultResponse(aiResponse, "https://acme.com/jobs/1", "raw text");

        assertEquals("Engineer", result.getTitle());
        assertFalse(result.isRequiresManualReview());
    }

    @Test
    void toResultResponse_MutatingAiSkillsList_DoesNotChangeResponse() {
        java.util.List<String> aiSkills = new java.util.ArrayList<>(java.util.List.of("Java"));
        JobExtractionAiResponse aiResponse = JobExtractionAiResponse.builder()
                .title("Engineer")
                .company("Acme")
                .skills(aiSkills)
                .build();

        JobExtractionResultResponse result =
                mapper.toResultResponse(aiResponse, "https://acme.com/jobs/1", "raw text");

        aiSkills.add("Hacked");
        assertEquals(java.util.List.of("Java"), result.getSkills());
    }

    @Test
    void toResultResponse_BothTitleAndCompanyPresent_RequiresManualReviewFalse() {
        JobExtractionAiResponse aiResponse = JobExtractionAiResponse.builder()
                .title("Engineer")
                .company("Acme")
                .salary("")
                .build();

        JobExtractionResultResponse result =
                mapper.toResultResponse(aiResponse, "https://acme.com/jobs/1", "raw text");

        assertFalse(result.isRequiresManualReview());
    }

    @Test
    void toResultResponse_SourceUrlAndOriginalDescription_ComeFromArgsNotAiResponse() {
        JobExtractionAiResponse aiResponse = JobExtractionAiResponse.builder()
                .title("Engineer")
                .company("Acme")
                .build();

        JobExtractionResultResponse result =
                mapper.toResultResponse(aiResponse, "https://normalized.example.com/job", "the raw text");

        assertEquals("https://normalized.example.com/job", result.getSourceUrl());
        assertEquals("the raw text", result.getOriginalDescription());
    }

    @Test
    void toResultResponse_NullSkills_MapsToEmptyMutableList() {
        JobExtractionAiResponse aiResponse = JobExtractionAiResponse.builder()
                .title("Engineer")
                .company("Acme")
                .skills(null)
                .build();

        JobExtractionResultResponse result =
                mapper.toResultResponse(aiResponse, "https://acme.com/jobs/1", "raw text");

        assertNotNull(result.getSkills());
        assertTrue(result.getSkills().isEmpty());
        assertDoesNotThrow(() -> result.getSkills().add("Java"));
    }

    @Test
    void toResultResponse_BlankTitle_RequiresManualReviewTrue() {
        JobExtractionAiResponse aiResponse = JobExtractionAiResponse.builder()
                .title("")
                .company("Acme")
                .build();

        JobExtractionResultResponse result =
                mapper.toResultResponse(aiResponse, "https://acme.com/jobs/1", "raw text");

        assertTrue(result.isRequiresManualReview());
    }

    @Test
    void toResultResponse_BlankCompany_RequiresManualReviewTrue() {
        JobExtractionAiResponse aiResponse = JobExtractionAiResponse.builder()
                .title("Engineer")
                .company(null)
                .build();

        JobExtractionResultResponse result =
                mapper.toResultResponse(aiResponse, "https://acme.com/jobs/1", "raw text");

        assertTrue(result.isRequiresManualReview());
    }
}

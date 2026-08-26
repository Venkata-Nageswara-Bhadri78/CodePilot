package com.developer.copilot.user.service.parsing;

import com.developer.copilot.user.entity.ResumeParsedData;
import com.developer.copilot.user.entity.ResumeParsingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeContextTextRendererTest {

    private ResumeContextTextRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new ResumeContextTextRenderer();
    }

    @Test
    void render_producesCandidateProfileText() {
        ResumeParsedData parsedData = completed();
        parsedData.setCandidateName("Jane Doe");
        parsedData.setEmail("jane@example.com");
        parsedData.setLocation("Berlin, Germany");

        Map<String, String> sections = new LinkedHashMap<>();
        sections.put(ResumeSection.SUMMARY.name(), "Backend engineer.");
        sections.put(ResumeSection.SKILLS.name(), "Java, Spring Boot");

        String rendered = renderer.render(parsedData, sections);

        assertTrue(rendered.contains("CANDIDATE RESUME PROFILE"));
        assertTrue(rendered.contains("NAME: Jane Doe"));
        assertTrue(rendered.contains("EMAIL: jane@example.com"));
        assertTrue(rendered.contains("LOCATION: Berlin, Germany"));
        assertTrue(rendered.contains("PROFESSIONAL SUMMARY"));
        assertTrue(rendered.contains("Backend engineer."));
        assertTrue(rendered.contains("CORE TECHNICAL SKILLS"));
        assertTrue(rendered.contains("Java, Spring Boot"));
    }

    @Test
    void render_rendersSectionsInCanonicalOrder() {
        ResumeParsedData parsedData = completed();

        Map<String, String> sections = new LinkedHashMap<>();
        sections.put(ResumeSection.EDUCATION.name(), "B.Tech");
        sections.put(ResumeSection.SUMMARY.name(), "Engineer.");

        String rendered = renderer.render(parsedData, sections);

        assertTrue(rendered.indexOf("PROFESSIONAL SUMMARY") < rendered.indexOf("EDUCATION"));
    }

    @Test
    void render_dropsContactLinesAlreadyShownAsFields() {
        ResumeParsedData parsedData = completed();
        parsedData.setCandidateName("Jane Doe");
        parsedData.setEmail("jane@example.com");

        Map<String, String> sections = new LinkedHashMap<>();
        sections.put(ResumeSection.CONTACT.name(), "Jane Doe\njane@example.com\nStaff Engineer");
        sections.put(ResumeSection.SUMMARY.name(), "Engineer.");

        String rendered = renderer.render(parsedData, sections);

        assertTrue(rendered.contains("Staff Engineer"));
        assertTrue(rendered.indexOf("Jane Doe") == rendered.lastIndexOf("Jane Doe"));
        assertTrue(rendered.indexOf("jane@example.com") == rendered.lastIndexOf("jane@example.com"));
    }

    @Test
    void render_withoutSections_fallsBackToRawText() {
        ResumeParsedData parsedData = completed();
        parsedData.setRawText("Unstructured resume body.");

        String rendered = renderer.render(parsedData, Map.of());

        assertTrue(rendered.contains("RESUME CONTENT"));
        assertTrue(rendered.contains("Unstructured resume body."));
    }

    @Test
    void render_pendingRecord_returnsNull() {
        ResumeParsedData parsedData = completed();
        parsedData.setStatus(ResumeParsingStatus.PENDING);

        assertNull(renderer.render(parsedData, Map.of()));
    }

    @Test
    void render_failedRecord_returnsNull() {
        ResumeParsedData parsedData = completed();
        parsedData.setStatus(ResumeParsingStatus.FAILED);

        assertNull(renderer.render(parsedData, Map.of()));
    }

    private ResumeParsedData completed() {
        return ResumeParsedData.builder()
                .status(ResumeParsingStatus.COMPLETED)
                .build();
    }
}

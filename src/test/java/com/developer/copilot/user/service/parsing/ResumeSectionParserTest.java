package com.developer.copilot.user.service.parsing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeSectionParserTest {

    private ResumeSectionParser parser;

    @BeforeEach
    void setUp() {
        parser = new ResumeSectionParser();
    }

    @Test
    void parse_detectsCanonicalSections() {
        String text = """
                Jane Doe
                jane.doe@example.com

                PROFESSIONAL SUMMARY
                Backend engineer with eight years of experience.

                TECHNICAL SKILLS
                Java, Spring Boot, PostgreSQL

                WORK EXPERIENCE
                Senior Engineer at Acme

                EDUCATION
                B.Tech Computer Science
                """;

        ParsedResumeContent content = parser.parse(text);

        assertEquals("Backend engineer with eight years of experience.",
                content.getSections().get(ResumeSection.SUMMARY));
        assertEquals("Java, Spring Boot, PostgreSQL",
                content.getSections().get(ResumeSection.SKILLS));
        assertEquals("Senior Engineer at Acme",
                content.getSections().get(ResumeSection.EXPERIENCE));
        assertEquals("B.Tech Computer Science",
                content.getSections().get(ResumeSection.EDUCATION));
    }

    @Test
    void parse_matchesHeadingSynonyms() {
        String text = """
                Jane Doe

                Career Objective
                Looking for backend roles.

                Core Competencies
                Kafka, Redis

                Employment History
                Engineer at Globex
                """;

        ParsedResumeContent content = parser.parse(text);

        assertEquals("Looking for backend roles.", content.getSections().get(ResumeSection.SUMMARY));
        assertEquals("Kafka, Redis", content.getSections().get(ResumeSection.SKILLS));
        assertEquals("Engineer at Globex", content.getSections().get(ResumeSection.EXPERIENCE));
    }

    @Test
    void parse_resolvesCompoundHeadingToFirstKnownPart() {
        String text = """
                Jane Doe

                EDUCATION & CERTIFICATIONS
                B.Tech, AWS Certified Solutions Architect
                """;

        ParsedResumeContent content = parser.parse(text);

        assertEquals("B.Tech, AWS Certified Solutions Architect",
                content.getSections().get(ResumeSection.EDUCATION));
    }

    @Test
    void parse_ignoresDecoratedHeadingRules() {
        String text = """
                ================================
                Jane Doe
                ================================

                -------- SKILLS --------
                Java
                """;

        ParsedResumeContent content = parser.parse(text);

        assertEquals("Java", content.getSections().get(ResumeSection.SKILLS));
        assertEquals("Jane Doe", content.getCandidateName());
    }

    @Test
    void parse_extractsContactDetails() {
        String text = """
                Venkata Nageswara Bhadri
                Bengaluru, India
                venkata@example.com
                +91 98765 43210
                linkedin.com/in/venkata-bhadri
                https://github.com/venkata-bhadri

                SUMMARY
                Engineer.
                """;

        ParsedResumeContent content = parser.parse(text);

        assertEquals("Venkata Nageswara Bhadri", content.getCandidateName());
        assertEquals("venkata@example.com", content.getEmail());
        assertEquals("+91 98765 43210", content.getPhone());
        assertEquals("Bengaluru, India", content.getLocation());
        assertEquals("linkedin.com/in/venkata-bhadri", content.getLinkedinUrl());
        assertEquals("https://github.com/venkata-bhadri", content.getGithubUrl());
    }

    @Test
    void parse_readsLabelledHeaderFields() {
        String text = """
                NAME: Jane Doe
                TITLE: Staff Engineer
                LOCATION: Berlin, Germany
                EMAIL: jane@example.com

                SUMMARY
                Engineer.
                """;

        ParsedResumeContent content = parser.parse(text);

        assertEquals("Jane Doe", content.getCandidateName());
        assertEquals("Berlin, Germany", content.getLocation());
        assertEquals("jane@example.com", content.getEmail());
    }

    @Test
    void parse_rejectsNonPhoneDigitRuns() {
        String text = """
                Jane Doe
                Graduated 2019

                SUMMARY
                Engineer.
                """;

        ParsedResumeContent content = parser.parse(text);

        assertNull(content.getPhone());
    }

    @Test
    void parse_withoutRecognizableHeadings_keepsEverythingAsContactBlock() {
        String text = """
                Some free form resume text
                that has no headings at all.
                """;

        ParsedResumeContent content = parser.parse(text);

        assertEquals(1, content.getSections().size());
        assertTrue(content.getSections().containsKey(ResumeSection.CONTACT));
        assertFalse(content.getSections().containsKey(ResumeSection.SUMMARY));
    }

    @Test
    void parse_blankInput_returnsEmptyContent() {
        ParsedResumeContent content = parser.parse("   ");

        assertTrue(content.getSections().isEmpty());
        assertNull(content.getCandidateName());
    }
}

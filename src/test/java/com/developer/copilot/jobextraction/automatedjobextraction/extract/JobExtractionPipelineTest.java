package com.developer.copilot.jobextraction.automatedjobextraction.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.developer.copilot.jobextraction.automatedjobextraction.exception.InvalidAutomatedJobUrlException;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.strategies.GenericMainContentStrategy;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.strategies.JsonLdJobExtractionStrategy;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.strategies.SemanticHtmlJobExtractionStrategy;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.strategies.WorkdayJobExtractionStrategy;
import com.developer.copilot.jobextraction.automatedjobextraction.fetch.FetchedJobPage;

class JobExtractionPipelineTest {

    private JobExtractionPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new JobExtractionPipeline(
                List.of(
                        new JsonLdJobExtractionStrategy(),
                        new SemanticHtmlJobExtractionStrategy(),
                        new GenericMainContentStrategy()),
                new HtmlNoiseStripper(),
                new JobContentQualityValidator(),
                new ExtractedJobTextFormatter());
    }

    @Test
    void jsonLdJobPosting_extractsTitleCompanyAndDescription() {
        String html = """
                <html>
                  <head>
                    <script type="application/ld+json">
                    {
                      "@context": "https://schema.org",
                      "@type": "JobPosting",
                      "title": "Software Engineer",
                      "description": "<p>Build APIs. Responsibilities include owning services.</p>",
                      "hiringOrganization": { "name": "Example Technologies" },
                      "jobLocation": { "address": { "addressLocality": "Bengaluru", "addressCountry": "India" } },
                      "employmentType": "FULL_TIME"
                    }
                    </script>
                  </head>
                  <body>
                    <nav>Careers Home Similar Jobs</nav>
                    <footer>Privacy cookies</footer>
                  </body>
                </html>
                """;

        String text = pipeline.extractJobText(
                "https://example.com/careers/software-engineer-12345",
                new FetchedJobPage("https://example.com/careers/software-engineer-12345", "text/html", html));

        assertTrue(text.contains("Software Engineer"));
        assertTrue(text.contains("Example Technologies"));
        assertTrue(text.contains("Bengaluru"));
        assertTrue(text.contains("FULL_TIME"));
        org.junit.jupiter.api.Assertions.assertFalse(text.toLowerCase().contains("privacy cookies"));
    }

    @Test
    void multipleJobPostings_prefersUrlMatch() {
        String html = """
                <html><head>
                <script type="application/ld+json">
                [
                  {"@type":"JobPosting","title":"Other Role","url":"https://example.com/jobs/other",
                   "description":"Other job description with responsibilities and requirements listed here."},
                  {"@type":"JobPosting","title":"Target Role","url":"https://example.com/jobs/target",
                   "description":"Target job description with responsibilities and requirements listed here."}
                ]
                </script>
                </head><body></body></html>
                """;

        String text = pipeline.extractJobText(
                "https://example.com/jobs/target",
                new FetchedJobPage("https://example.com/jobs/target", "text/html", html));

        assertTrue(text.contains("Target Role"));
        org.junit.jupiter.api.Assertions.assertFalse(text.contains("Other Role"));
    }

    @Test
    void listingPageWithoutJobBody_rejected() {
        String html = """
                <html><body>
                  <h1>Open positions</h1>
                  <a href="/jobs/1">Job 1</a><a href="/jobs/2">Job 2</a>
                  <a href="/jobs/3">Job 3</a><a href="/jobs/4">Job 4</a>
                  <a href="/jobs/5">Job 5</a><a href="/jobs/6">Job 6</a>
                  <a href="/jobs/7">Job 7</a><a href="/jobs/8">Job 8</a>
                  <p>Search jobs at our company.</p>
                </body></html>
                """;

        assertThrows(InvalidAutomatedJobUrlException.class, () -> pipeline.extractJobText(
                "https://example.com/careers",
                new FetchedJobPage("https://example.com/careers", "text/html", html)));
    }

    @Test
    void emptyBody_rejected() {
        assertThrows(InvalidAutomatedJobUrlException.class, () -> pipeline.extractJobText(
                "https://example.com/jobs/1",
                new FetchedJobPage("https://example.com/jobs/1", "text/html", "  ")));
    }

    @Test
    void semanticArticle_extractsMainJobContentAndDropsNav() {
        String html = """
                <html><body>
                  <nav>Home Careers Cookie settings</nav>
                  <article>
                    <h1>Backend Engineer</h1>
                    <h2>Responsibilities</h2>
                    <ul><li>Design APIs</li><li>Review code</li></ul>
                    <h2>Requirements</h2>
                    <ul><li>Java</li><li>SQL</li></ul>
                    <p>This role is a full-time software engineering job description covering the position in depth
                    so that applicants understand the qualifications and experience expected.</p>
                  </article>
                  <h2>Similar jobs</h2>
                  <div class="related-jobs"><a href="/jobs/9">Other</a></div>
                </body></html>
                """;

        String text = pipeline.extractJobText(
                "https://company.com/careers/backend-engineer",
                new FetchedJobPage("https://company.com/careers/backend-engineer", "text/html", html));

        assertTrue(text.contains("Backend Engineer"));
        assertTrue(text.contains("Design APIs"));
        assertTrue(text.contains("Java"));
        org.junit.jupiter.api.Assertions.assertFalse(text.contains("Cookie settings"));
    }

    @Test
    void jsonPayloadJobPosting_supported() {
        String json = """
                {"@type":"JobPosting","title":"Staff Engineer","hiringOrganization":{"name":"Acme"},
                 "description":"Own the platform. Responsibilities include reliability. Requirements include Java."}
                """;
        String text = pipeline.extractJobText(
                "https://api.example.com/jobs/1",
                new FetchedJobPage("https://api.example.com/jobs/1", "application/json", json));
        assertTrue(text.contains("Staff Engineer"));
        assertTrue(text.contains("Acme"));
    }

    @Test
    void workdayJobPostingInfoJson_extractsTitleAndDescription() {
        JobExtractionPipeline workdayPipeline = new JobExtractionPipeline(
                List.of(
                        new JsonLdJobExtractionStrategy(),
                        new WorkdayJobExtractionStrategy(),
                        new GenericMainContentStrategy()),
                new HtmlNoiseStripper(),
                new JobContentQualityValidator(),
                new ExtractedJobTextFormatter());
        String json = """
                {"jobPostingInfo":{"title":"Program Manager",
                 "jobDescription":"<p>Lead programs. Responsibilities include planning. Requirements include PMP.</p>",
                 "location":"Austin, TX","timeType":"Full time","jobReqId":"R-1"},
                 "hiringOrganization":{"name":"Acme"}}
                """;
        String text = workdayPipeline.extractJobText(
                "https://acme.wd1.myworkdayjobs.com/en-US/External/job/Austin/Program-Manager_R-1",
                new FetchedJobPage(
                        "https://acme.wd1.myworkdayjobs.com/en-US/External/job/Austin/Program-Manager_R-1",
                        "application/json",
                        json));
        assertTrue(text.contains("Program Manager"));
        assertTrue(text.contains("Acme"));
        assertTrue(text.contains("planning"));
    }

    @Test
    void formatterOmitsMissingFields() {
        ExtractedJobTextFormatter formatter = new ExtractedJobTextFormatter();
        ExtractedJobFields fields = new ExtractedJobFields();
        fields.setTitle("T");
        fields.setDescription("A reasonably long job description about responsibilities and requirements.");
        String text = formatter.format(fields);
        assertTrue(text.contains("Job Title: T"));
        org.junit.jupiter.api.Assertions.assertFalse(text.contains("Salary:"));
        org.junit.jupiter.api.Assertions.assertFalse(text.contains("null"));
    }

    @Test
    void fieldMergerDoesNotOverwriteAuthoritativeTitle() {
        ExtractedJobFields primary = new ExtractedJobFields();
        primary.setTitle("From JSON-LD");
        primary.setFromStructuredData(true);
        ExtractedJobFields extra = new ExtractedJobFields();
        extra.setTitle("From H1");
        extra.setCompany("Acme");
        ExtractedJobFields merged = FieldMerger.merge(primary, extra);
        assertEquals("From JSON-LD", merged.getTitle());
        assertEquals("Acme", merged.getCompany());
        assertTrue(merged.isFromStructuredData());
    }
}

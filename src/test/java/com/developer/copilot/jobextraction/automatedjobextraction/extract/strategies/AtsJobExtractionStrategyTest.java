package com.developer.copilot.jobextraction.automatedjobextraction.extract.strategies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractedJobFields;

class AtsJobExtractionStrategyTest {

    @Test
    void greenhouse_readsAppTitleAndContent() {
        String html = """
                <html><body>
                  <h1 class="app-title">Platform Engineer</h1>
                  <div class="location">Remote</div>
                  <div id="content" class="content">
                    Job description with responsibilities and requirements for a greenhouse posting.
                    Applicants should have several years of experience building distributed systems.
                  </div>
                </body></html>
                """;
        Document doc = Jsoup.parse(html, "https://boards.greenhouse.io/acme/jobs/1");
        GreenhouseJobExtractionStrategy strategy = new GreenhouseJobExtractionStrategy();
        URI url = URI.create("https://boards.greenhouse.io/acme/jobs/1");
        assertTrue(strategy.supports(url, doc, doc));
        ExtractedJobFields fields = strategy.extract(url, doc, doc);
        assertEquals("Platform Engineer", fields.getTitle());
        assertTrue(fields.getDescription().contains("distributed systems"));
    }

    @Test
    void lever_readsPostingHeadline() {
        String html = """
                <html><body>
                  <div class="posting-headline"><h2>Data Analyst</h2></div>
                  <div class="posting-categories"><div class="location">NYC</div>
                  <div class="commitment">Full-time</div></div>
                  <div class="section-wrapper">Role details and responsibilities go here with enough text
                  to describe the position thoroughly including requirements.</div>
                </body></html>
                """;
        Document doc = Jsoup.parse(html, "https://jobs.lever.co/acme/abc");
        LeverJobExtractionStrategy strategy = new LeverJobExtractionStrategy();
        URI url = URI.create("https://jobs.lever.co/acme/abc");
        assertTrue(strategy.supports(url, doc, doc));
        ExtractedJobFields fields = strategy.extract(url, doc, doc);
        assertEquals("Data Analyst", fields.getTitle());
        assertEquals("NYC", fields.getLocation());
        assertEquals("Full-time", fields.getEmploymentType());
    }

    @Test
    void workday_readsAutomationIds() {
        String html = """
                <html><body>
                  <h2 data-automation-id="jobPostingHeader">Program Manager</h2>
                  <div data-automation-id="locations">Austin, TX</div>
                  <div data-automation-id="jobPostingDescription">
                    Full job description covering responsibilities and requirements for this Workday posting.
                  </div>
                </body></html>
                """;
        Document doc = Jsoup.parse(html, "https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/x");
        WorkdayJobExtractionStrategy strategy = new WorkdayJobExtractionStrategy();
        URI url = URI.create("https://visa.wd5.myworkdayjobs.com/en-US/Visa/details/x");
        assertTrue(strategy.supports(url, doc, doc));
        ExtractedJobFields fields = strategy.extract(url, doc, doc);
        assertEquals("Program Manager", fields.getTitle());
        assertEquals("Austin, TX", fields.getLocation());
    }

    @Test
    void workday_readsJobPostingInfoJson() {
        String json = """
                {"jobPostingInfo":{
                   "title":"DevOps Engineer",
                   "jobDescription":"<p>Run platforms. Responsibilities include CI and requirements for Kubernetes.</p>",
                   "location":"Remote",
                   "timeType":"Full time",
                   "jobReqId":"JR1",
                   "externalUrl":"https://nvidia.wd5.myworkdayjobs.com/site/job/JR1"
                 },
                 "hiringOrganization":{"name":"NVIDIA"}}
                """;
        String html = "<html><head><script type=\"application/ld+json\">" + json
                + "</script></head><body></body></html>";
        Document doc = Jsoup.parse(html, "https://nvidia.wd5.myworkdayjobs.com/site/job/JR1");
        WorkdayJobExtractionStrategy strategy = new WorkdayJobExtractionStrategy();
        URI url = URI.create("https://nvidia.wd5.myworkdayjobs.com/site/job/JR1");
        assertTrue(strategy.supports(url, doc, doc));
        ExtractedJobFields fields = strategy.extract(url, doc, doc);
        assertEquals("DevOps Engineer", fields.getTitle());
        assertEquals("NVIDIA", fields.getCompany());
        assertEquals("Remote", fields.getLocation());
        assertEquals("JR1", fields.getJobId());
        assertTrue(fields.getDescription().contains("Kubernetes"));
        assertTrue(fields.isFromStructuredData());
    }

    @Test
    void indeed_readsJobDescriptionText() {
        String html = """
                <html><body>
                  <h1 class="jobsearch-JobInfoHeader-title">QA Engineer</h1>
                  <div id="jobDescriptionText">Indeed posting body with qualifications and responsibilities
                  described in enough detail for applicants to evaluate fit.</div>
                </body></html>
                """;
        Document doc = Jsoup.parse(html, "https://www.indeed.com/viewjob?jk=abc");
        IndeedJobExtractionStrategy strategy = new IndeedJobExtractionStrategy();
        URI url = URI.create("https://www.indeed.com/viewjob?jk=abc");
        assertTrue(strategy.supports(url, doc, doc));
        assertEquals("QA Engineer", strategy.extract(url, doc, doc).getTitle());
    }

    @Test
    void linkedin_readsJobDetails() {
        String html = """
                <html><body>
                  <h1 class="top-card-layout__title">iOS Developer</h1>
                  <a class="topcard__org-name-link">Acme</a>
                  <div class="show-more-less-html__markup">LinkedIn job description with requirements
                  and responsibilities written out for candidates.</div>
                </body></html>
                """;
        Document doc = Jsoup.parse(html, "https://www.linkedin.com/jobs/view/123");
        LinkedInJobExtractionStrategy strategy = new LinkedInJobExtractionStrategy();
        URI url = URI.create("https://www.linkedin.com/jobs/view/123");
        assertTrue(strategy.supports(url, doc, doc));
        ExtractedJobFields fields = strategy.extract(url, doc, doc);
        assertEquals("iOS Developer", fields.getTitle());
        assertEquals("Acme", fields.getCompany());
    }
}

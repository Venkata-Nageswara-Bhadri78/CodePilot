package com.developer.copilot.jobextraction.automatedjobextraction.extract.strategies;

import java.net.URI;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractedJobFields;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractionSupport;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.JobExtractionStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Order(20)
public class WorkdayJobExtractionStrategy implements JobExtractionStrategy {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(URI url, Document original, Document cleaned) {
        if (url != null && (ExtractionSupport.hostContains(url.getHost(), "myworkdayjobs.com")
                || ExtractionSupport.hostContains(url.getHost(), "workdayjobs.com")
                || ExtractionSupport.hostContains(url.getHost(), "myworkdaysite.com"))) {
            return true;
        }
        Document doc = original != null ? original : cleaned;
        return doc != null && doc.html() != null && doc.html().contains("jobPostingInfo");
    }

    @Override
    public ExtractedJobFields extract(URI url, Document original, Document cleaned) {
        Document doc = original != null ? original : cleaned;
        ExtractedJobFields fromJson = extractFromWorkdayJson(doc, url);
        if (fromJson != null && ExtractionSupport.cleanText(fromJson.getTitle()) != null) {
            return fromJson;
        }
        ExtractedJobFields fields = fromJson == null ? new ExtractedJobFields() : fromJson;
        fields.setTitle(firstNonBlank(fields.getTitle(), ExtractionSupport.firstText(doc,
                "[data-automation-id=jobPostingHeader], [data-automation-id=jobPostingTitle], h2, h1")));
        fields.setLocation(firstNonBlank(fields.getLocation(), ExtractionSupport.firstText(doc,
                "[data-automation-id=locations], [data-automation-id=jobPostingLocation]")));
        fields.setPostedDate(firstNonBlank(fields.getPostedDate(),
                ExtractionSupport.firstText(doc, "[data-automation-id=postedOn]")));
        fields.setJobId(firstNonBlank(fields.getJobId(),
                ExtractionSupport.firstText(doc, "[data-automation-id=requisitionId]")));
        fields.setDescription(firstNonBlank(fields.getDescription(), ExtractionSupport.joinedText(doc,
                "[data-automation-id=jobPostingDescription], [data-automation-id=jobPostingPage]")));
        if (url != null) {
            fields.setSourceUrl(firstNonBlank(fields.getSourceUrl(), url.toString()));
            fields.setApplicationUrl(firstNonBlank(fields.getApplicationUrl(), url.toString()));
        }
        return fields;
    }

    private ExtractedJobFields extractFromWorkdayJson(Document doc, URI url) {
        if (doc == null) {
            return null;
        }
        for (Element script : doc.select("script")) {
            ExtractedJobFields parsed = parseJobPostingInfo(script.data(), url);
            if (parsed != null) {
                return parsed;
            }
        }
        if (doc.body() != null) {
            return parseJobPostingInfo(doc.body().text(), url);
        }
        return null;
    }

    private ExtractedJobFields parseJobPostingInfo(String raw, URI url) {
        if (raw == null || !raw.contains("jobPostingInfo")) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(raw.trim());
            JsonNode info = root.get("jobPostingInfo");
            if (info == null || !info.isObject()) {
                return null;
            }
            ExtractedJobFields fields = new ExtractedJobFields();
            fields.setFromStructuredData(true);
            fields.setTitle(text(info, "title"));
            fields.setDescription(ExtractionSupport.htmlToText(text(info, "jobDescription")));
            fields.setLocation(text(info, "location"));
            fields.setPostedDate(firstNonBlank(text(info, "postedOn"), text(info, "startDate")));
            fields.setEmploymentType(text(info, "timeType"));
            fields.setJobId(text(info, "jobReqId"));
            fields.setApplicationDeadline(firstNonBlank(
                    text(info, "jobPostingEndDateAsText"), text(info, "endDate")));
            JsonNode org = root.get("hiringOrganization");
            if (org != null && org.isObject()) {
                fields.setCompany(text(org, "name"));
            }
            String apply = text(info, "externalUrl");
            if (url != null) {
                fields.setSourceUrl(url.toString());
                fields.setApplicationUrl(firstNonBlank(apply, url.toString()));
            } else {
                fields.setApplicationUrl(apply);
            }
            return fields;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return ExtractionSupport.cleanText(node.get(field).asText());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

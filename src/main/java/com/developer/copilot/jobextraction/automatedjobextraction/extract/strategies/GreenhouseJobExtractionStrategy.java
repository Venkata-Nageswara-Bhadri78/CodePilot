package com.developer.copilot.jobextraction.automatedjobextraction.extract.strategies;

import java.net.URI;

import org.jsoup.nodes.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractedJobFields;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractionSupport;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.JobExtractionStrategy;

@Component
@Order(20)
public class GreenhouseJobExtractionStrategy implements JobExtractionStrategy {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(URI url, Document original, Document cleaned) {
        if (hostContains(url, "greenhouse.io")) {
            return true;
        }
        Document doc = original != null ? original : cleaned;
        return doc != null && doc.selectFirst("#app_body, .app-title, #content.content") != null;
    }

    @Override
    public ExtractedJobFields extract(URI url, Document original, Document cleaned) {
        Document doc = cleaned != null ? cleaned : original;
        ExtractedJobFields fields = new ExtractedJobFields();
        fields.setTitle(first(doc, "h1.app-title, .app-title, .job__title, h1"));
        fields.setLocation(first(doc, ".location, .job__location, .app-location"));
        fields.setCompany(first(doc, ".company-name, .app-company"));
        fields.setDescription(firstLong(doc, "#content, .content, #job_description, .job__description"));
        if (url != null) {
            fields.setSourceUrl(url.toString());
            fields.setApplicationUrl(url.toString());
        }
        return fields;
    }

    private static boolean hostContains(URI url, String fragment) {
        return url != null && ExtractionSupport.hostContains(url.getHost(), fragment);
    }

    private static String first(Document doc, String css) {
        return ExtractionSupport.firstText(doc, css);
    }

    private static String firstLong(Document doc, String css) {
        return ExtractionSupport.joinedText(doc, css);
    }
}

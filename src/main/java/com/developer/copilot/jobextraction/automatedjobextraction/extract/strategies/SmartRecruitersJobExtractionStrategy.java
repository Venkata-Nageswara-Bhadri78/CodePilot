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
public class SmartRecruitersJobExtractionStrategy implements JobExtractionStrategy {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(URI url, Document original, Document cleaned) {
        return url != null && ExtractionSupport.hostContains(url.getHost(), "smartrecruiters.com");
    }

    @Override
    public ExtractedJobFields extract(URI url, Document original, Document cleaned) {
        Document doc = cleaned != null ? cleaned : original;
        ExtractedJobFields fields = new ExtractedJobFields();
        fields.setTitle(ExtractionSupport.firstText(doc, "h1.job-title, h1"));
        fields.setLocation(ExtractionSupport.firstText(doc, ".job-location, li.location"));
        fields.setCompany(ExtractionSupport.firstText(doc, ".job-company, .company-name"));
        fields.setDescription(ExtractionSupport.joinedText(doc, ".job-sections, .job-description, article"));
        if (url != null) {
            fields.setSourceUrl(url.toString());
            fields.setApplicationUrl(url.toString());
        }
        return fields;
    }
}

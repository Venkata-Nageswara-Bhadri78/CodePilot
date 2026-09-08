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
public class LinkedInJobExtractionStrategy implements JobExtractionStrategy {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(URI url, Document original, Document cleaned) {
        return url != null && ExtractionSupport.hostContains(url.getHost(), "linkedin.com");
    }

    @Override
    public ExtractedJobFields extract(URI url, Document original, Document cleaned) {
        Document doc = original != null ? original : cleaned;
        ExtractedJobFields fields = new ExtractedJobFields();
        fields.setTitle(ExtractionSupport.firstText(doc,
                "h1.top-card-layout__title, h1.jobs-unified-top-card__job-title, h1"));
        fields.setCompany(ExtractionSupport.firstText(doc,
                ".topcard__org-name-link, .jobs-unified-top-card__company-name, a.topcard__org-name-link"));
        fields.setLocation(ExtractionSupport.firstText(doc,
                ".topcard__flavor--bullet, .jobs-unified-top-card__bullet"));
        fields.setDescription(ExtractionSupport.joinedText(doc,
                ".show-more-less-html__markup, .jobs-description__content, .description__text, #job-details"));
        if (url != null) {
            fields.setSourceUrl(url.toString());
            fields.setApplicationUrl(url.toString());
        }
        return fields;
    }
}

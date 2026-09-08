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
public class IndeedJobExtractionStrategy implements JobExtractionStrategy {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(URI url, Document original, Document cleaned) {
        return url != null && ExtractionSupport.hostContains(url.getHost(), "indeed.com");
    }

    @Override
    public ExtractedJobFields extract(URI url, Document original, Document cleaned) {
        Document doc = original != null ? original : cleaned;
        ExtractedJobFields fields = new ExtractedJobFields();
        fields.setTitle(ExtractionSupport.firstText(doc,
                "h1.jobsearch-JobInfoHeader-title, h2.jobsearch-JobInfoHeader-title, h1"));
        fields.setCompany(ExtractionSupport.firstText(doc, "[data-company-name=true], .jobsearch-InlineCompanyRating"));
        fields.setLocation(ExtractionSupport.firstText(doc, "[data-testid=inlineHeader-companyLocation]"));
        fields.setDescription(ExtractionSupport.joinedText(doc, "#jobDescriptionText, #jobDescription"));
        if (url != null) {
            fields.setSourceUrl(url.toString());
            fields.setApplicationUrl(url.toString());
        }
        return fields;
    }
}

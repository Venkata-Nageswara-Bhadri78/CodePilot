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
public class AshbyJobExtractionStrategy implements JobExtractionStrategy {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(URI url, Document original, Document cleaned) {
        return url != null && ExtractionSupport.hostContains(url.getHost(), "ashbyhq.com");
    }

    @Override
    public ExtractedJobFields extract(URI url, Document original, Document cleaned) {
        Document doc = cleaned != null ? cleaned : original;
        ExtractedJobFields fields = new ExtractedJobFields();
        fields.setTitle(ExtractionSupport.firstText(doc, "h1, [class*=job-title]"));
        fields.setLocation(ExtractionSupport.firstText(doc, "[class*=location]"));
        fields.setDescription(ExtractionSupport.joinedText(doc, "[class*=JobPosting], [class*=job-description], article, main"));
        if (url != null) {
            fields.setSourceUrl(url.toString());
            fields.setApplicationUrl(url.toString());
        }
        return fields;
    }
}

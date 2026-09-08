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
public class LeverJobExtractionStrategy implements JobExtractionStrategy {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(URI url, Document original, Document cleaned) {
        return url != null && ExtractionSupport.hostContains(url.getHost(), "lever.co");
    }

    @Override
    public ExtractedJobFields extract(URI url, Document original, Document cleaned) {
        Document doc = cleaned != null ? cleaned : original;
        ExtractedJobFields fields = new ExtractedJobFields();
        fields.setTitle(ExtractionSupport.firstText(doc, ".posting-headline h2, .posting-headline, h2"));
        fields.setLocation(ExtractionSupport.firstText(doc, ".posting-categories .location, .location"));
        fields.setEmploymentType(ExtractionSupport.firstText(doc, ".posting-categories .commitment, .commitment"));
        fields.setDepartment(ExtractionSupport.firstText(doc, ".posting-categories .department, .department"));
        fields.setWorkMode(ExtractionSupport.firstText(doc, ".posting-categories .workplaceTypes"));
        fields.setDescription(ExtractionSupport.joinedText(doc, ".section-wrapper, .posting-page"));
        if (url != null) {
            fields.setSourceUrl(url.toString());
            fields.setApplicationUrl(url.toString());
        }
        return fields;
    }
}

package com.developer.copilot.jobextraction.automatedjobextraction.extract.strategies;

import java.net.URI;

import org.jsoup.nodes.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractedJobFields;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractionSupport;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.JobExtractionStrategy;

/**
 * Last-resort body text from the cleaned document. Always supports so unknown career
 * sites still produce a candidate that the quality validator can accept or reject.
 */
@Component
@Order(100)
public class GenericMainContentStrategy implements JobExtractionStrategy {

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean supports(URI url, Document original, Document cleaned) {
        return cleaned != null || original != null;
    }

    @Override
    public ExtractedJobFields extract(URI url, Document original, Document cleaned) {
        Document doc = cleaned != null ? cleaned : original;
        ExtractedJobFields fields = new ExtractedJobFields();
        if (doc == null) {
            return fields;
        }
        fields.setTitle(ExtractionSupport.firstText(doc, "h1"));
        if (fields.getTitle() == null) {
            fields.setTitle(ExtractionSupport.cleanText(doc.title()));
        }
        String body = ExtractionSupport.cleanText(doc.body() == null ? doc.text() : doc.body().text());
        fields.setDescription(body);
        if (url != null) {
            fields.setSourceUrl(url.toString());
        }
        return fields;
    }
}

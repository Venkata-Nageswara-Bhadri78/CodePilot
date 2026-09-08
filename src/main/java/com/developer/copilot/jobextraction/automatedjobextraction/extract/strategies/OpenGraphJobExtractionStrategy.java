package com.developer.copilot.jobextraction.automatedjobextraction.extract.strategies;

import java.net.URI;

import org.jsoup.nodes.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractedJobFields;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractionSupport;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.JobExtractionStrategy;

/**
 * Open Graph / standard page metadata. Used only to fill gaps, never as the sole job body.
 */
@Component
@Order(30)
public class OpenGraphJobExtractionStrategy implements JobExtractionStrategy {

    @Override
    public int order() {
        return 30;
    }

    @Override
    public boolean supports(URI url, Document original, Document cleaned) {
        return original != null && (original.selectFirst("meta[property^=og:]") != null
                || original.selectFirst("meta[name=description]") != null);
    }

    @Override
    public ExtractedJobFields extract(URI url, Document original, Document cleaned) {
        ExtractedJobFields fields = new ExtractedJobFields();
        fields.setTitle(first(
                ExtractionSupport.meta(original, "meta[property=og:title]", "content"),
                ExtractionSupport.meta(original, "meta[name=twitter:title]", "content"),
                ExtractionSupport.meta(original, "meta[name=title]", "content")));
        fields.setCompany(ExtractionSupport.meta(original, "meta[property=og:site_name]", "content"));
        String description = first(
                ExtractionSupport.meta(original, "meta[property=og:description]", "content"),
                ExtractionSupport.meta(original, "meta[name=description]", "content"));
        // Metadata is not job-specific body; keep it only as a last-resort title/company signal.
        if (fields.getTitle() == null) {
            fields.setTitle(description);
        }
        if (url != null) {
            fields.setSourceUrl(url.toString());
            fields.setApplicationUrl(first(
                    ExtractionSupport.meta(original, "meta[property=og:url]", "content"),
                    url.toString()));
        }
        return fields;
    }

    private static String first(String... values) {
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

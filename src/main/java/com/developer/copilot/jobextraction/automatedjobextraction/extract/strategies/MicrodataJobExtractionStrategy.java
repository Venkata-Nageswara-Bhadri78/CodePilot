package com.developer.copilot.jobextraction.automatedjobextraction.extract.strategies;

import java.net.URI;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractedJobFields;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractionSupport;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.JobExtractionStrategy;

/**
 * Microdata {@code itemtype=JobPosting} when JSON-LD is absent.
 */
@Component
@Order(10)
public class MicrodataJobExtractionStrategy implements JobExtractionStrategy {

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean supports(URI url, Document original, Document cleaned) {
        return original != null && jobPostingRoot(original) != null;
    }

    @Override
    public ExtractedJobFields extract(URI url, Document original, Document cleaned) {
        ExtractedJobFields fields = new ExtractedJobFields();
        Element root = jobPostingRoot(original);
        if (root == null) {
            return fields;
        }
        fields.setFromStructuredData(true);
        fields.setTitle(itemprop(root, "title"));
        if (fields.getTitle() == null) {
            fields.setTitle(itemprop(root, "name"));
        }
        fields.setDescription(itemprop(root, "description"));
        fields.setEmploymentType(itemprop(root, "employmentType"));
        fields.setPostedDate(itemprop(root, "datePosted"));
        fields.setApplicationDeadline(itemprop(root, "validThrough"));
        fields.setCompany(itemprop(root, "hiringOrganization"));
        fields.setLocation(itemprop(root, "jobLocation"));
        fields.setSalary(itemprop(root, "baseSalary"));
        if (url != null) {
            fields.setSourceUrl(url.toString());
        }
        return fields;
    }

    private static Element jobPostingRoot(Document document) {
        Element el = document.selectFirst("[itemtype*=JobPosting]");
        if (el != null) {
            return el;
        }
        return document.selectFirst("[itemtype*=jobposting]");
    }

    private static String itemprop(Element root, String name) {
        Element el = root.selectFirst("[itemprop=" + name + "]");
        if (el == null) {
            return null;
        }
        String content = ExtractionSupport.cleanText(el.attr("content"));
        if (content != null) {
            return content;
        }
        return ExtractionSupport.cleanText(el.text());
    }
}

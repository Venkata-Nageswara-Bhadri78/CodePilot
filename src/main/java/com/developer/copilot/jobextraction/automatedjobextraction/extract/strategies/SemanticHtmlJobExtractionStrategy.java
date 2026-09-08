package com.developer.copilot.jobextraction.automatedjobextraction.extract.strategies;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractedJobFields;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.ExtractionSupport;
import com.developer.copilot.jobextraction.automatedjobextraction.extract.JobExtractionStrategy;

/**
 * Company career sites: prefer {@code main}/{@code article} and common job-section headings.
 */
@Component
@Order(40)
public class SemanticHtmlJobExtractionStrategy implements JobExtractionStrategy {

    private static final String MAIN_SELECTORS = String.join(",",
            "main", "article", "[role=main]", ".job-description", "#job-description",
            ".jobDescription", ".job_description", ".posting", ".career-job",
            "[class*=job-detail]", "[class*=jobDetail]", "[id*=job-description]");

    @Override
    public int order() {
        return 40;
    }

    @Override
    public boolean supports(URI url, Document original, Document cleaned) {
        Document doc = cleaned != null ? cleaned : original;
        return doc != null && doc.selectFirst(MAIN_SELECTORS) != null;
    }

    @Override
    public ExtractedJobFields extract(URI url, Document original, Document cleaned) {
        Document doc = cleaned != null ? cleaned : original;
        ExtractedJobFields fields = new ExtractedJobFields();
        Element main = doc.selectFirst(MAIN_SELECTORS);
        Document scope = main == null ? doc : cloneAsDocument(main, url);
        fields.setTitle(ExtractionSupport.firstText(scope, "h1"));
        if (fields.getTitle() == null) {
            fields.setTitle(ExtractionSupport.firstText(scope, "h2"));
        }
        fields.setDescription(ExtractionSupport.cleanText(scope.text()));
        fields.setResponsibilities(sectionList(scope, "responsibilit"));
        List<String> requirements = sectionList(scope, "requirement");
        if (requirements.isEmpty()) {
            requirements = sectionList(scope, "qualification");
        }
        fields.setRequirements(requirements);
        fields.setBenefits(sectionList(scope, "benefit"));
        if (url != null) {
            fields.setSourceUrl(url.toString());
        }
        return fields;
    }

    private static Document cloneAsDocument(Element main, URI url) {
        String base = url == null ? "" : url.toString();
        return org.jsoup.Jsoup.parse(main.outerHtml(), base);
    }

    private static List<String> sectionList(Document scope, String headingFragment) {
        for (Element heading : scope.select("h1,h2,h3,h4,h5,h6,strong")) {
            String text = heading.text();
            if (text != null && text.toLowerCase(Locale.ROOT).contains(headingFragment)) {
                Element sibling = heading.nextElementSibling();
                if (sibling != null && ("ul".equals(sibling.normalName()) || "ol".equals(sibling.normalName()))) {
                    return ExtractionSupport.listItems(
                            org.jsoup.Jsoup.parseBodyFragment(sibling.outerHtml()), "li");
                }
                Element parent = heading.parent();
                if (parent != null) {
                    List<String> items = ExtractionSupport.listItems(parent, "li");
                    if (!items.isEmpty()) {
                        return items;
                    }
                }
            }
        }
        return new java.util.ArrayList<>();
    }
}

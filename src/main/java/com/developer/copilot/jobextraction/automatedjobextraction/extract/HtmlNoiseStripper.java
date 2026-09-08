package com.developer.copilot.jobextraction.automatedjobextraction.extract;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.springframework.stereotype.Component;

/**
 * Removes navigation, chrome, ads, related jobs, cookies, and other non-posting markup
 * before generic HTML extraction.
 */
@Component
public class HtmlNoiseStripper {

    static final String REMOVE_SELECTORS = String.join(",",
            "script", "style", "noscript", "svg", "canvas", "iframe",
            "nav", "header", "footer", "aside",
            "[role=navigation]", "[role=banner]", "[role=contentinfo]", "[role=complementary]",
            "form", "button", "input", "select", "textarea",
            ".cookie", "#cookie", "[class*=cookie]", "[id*=cookie]",
            "[class*=consent]", "[id*=consent]",
            "[class*=newsletter]", "[id*=newsletter]",
            "[class*=advert]", "[class*=adsbygoogle]", "[id*=google_ads]",
            "[class*=related-job]", "[class*=similar-job]", "[class*=recommended-job]",
            "[class*=recently-viewed]", "[class*=similarjobs]", "[class*=relatedjobs]",
            "[id*=related-job]", "[id*=similar-job]", "[id*=recommended]",
            ".social-share", "[class*=share-button]", "[class*=social-share]",
            "[class*=breadcrumb]", "[class*=pagination]",
            "[class*=promo]", "[class*=marketing]",
            "[aria-hidden=true]"
    );

    private static final Set<String> RELATED_HEADING_MARKERS = Set.of(
            "similar jobs",
            "related jobs",
            "recommended jobs",
            "recently viewed",
            "more jobs",
            "other jobs",
            "jobs you may like",
            "people also viewed"
    );

    public Document strip(Document source) {
        Document copy = source.clone();
        copy.select(REMOVE_SELECTORS).remove();
        removeRelatedJobSections(copy);
        return copy;
    }

    private static void removeRelatedJobSections(Document document) {
        for (Element heading : document.select("h1,h2,h3,h4,h5,h6")) {
            String text = heading.text();
            if (text == null) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT).trim();
            if (RELATED_HEADING_MARKERS.contains(lower) || looksLikeRelatedHeading(lower)) {
                Element sibling = heading.nextElementSibling();
                heading.remove();
                if (sibling != null && !sibling.is("article, main, [role=main]")) {
                    sibling.remove();
                }
            }
        }
    }

    private static boolean looksLikeRelatedHeading(String lower) {
        return (lower.contains("similar") || lower.contains("related") || lower.contains("recommended"))
                && lower.contains("job");
    }
}

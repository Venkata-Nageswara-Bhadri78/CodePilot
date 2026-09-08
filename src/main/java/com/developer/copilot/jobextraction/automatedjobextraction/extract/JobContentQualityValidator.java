package com.developer.copilot.jobextraction.automatedjobextraction.extract;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.util.AutomatedJobExtractionLimits;

/**
 * Decides whether extracted content is a real, identifiable job posting. Listing pages,
 * login walls, and generic careers chrome are rejected.
 */
@Component
public class JobContentQualityValidator {

    private static final Pattern JOB_PATH = Pattern.compile(
            "(job|jobs|career|careers|position|opening|vacancy|requisition|posting|opportunity)",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> JOB_MARKERS = List.of(
            "responsibilities",
            "requirements",
            "qualifications",
            "job description",
            "about the role",
            "what you will do",
            "who you are",
            "apply now",
            "employment type",
            "full-time",
            "full time",
            "part-time",
            "experience"
    );

    public boolean isIdentifiableJob(URI url, ExtractedJobFields fields, String formattedText, Document original) {
        if (fields == null || formattedText == null || formattedText.isBlank()) {
            return false;
        }
        if (formattedText.trim().length() < AutomatedJobExtractionLimits.MIN_JOB_TEXT_LENGTH) {
            return false;
        }
        if (fields.isFromStructuredData() && hasTitleOrCompany(fields)) {
            return true;
        }
        if (looksLikeListingPage(original, fields)) {
            return false;
        }
        boolean hasTitle = notBlank(fields.getTitle());
        boolean hasDescription = notBlank(fields.getDescription())
                && fields.getDescription().trim().length() >= AutomatedJobExtractionLimits.MIN_JOB_TEXT_LENGTH;
        if (hasTitle && hasDescription) {
            return true;
        }
        if (hasTitle && markerScore(formattedText) >= 2) {
            return true;
        }
        if (url != null && url.getPath() != null && JOB_PATH.matcher(url.getPath()).find()
                && markerScore(formattedText) >= 2
                && formattedText.trim().length() >= AutomatedJobExtractionLimits.MIN_JOB_TEXT_LENGTH * 2) {
            return true;
        }
        return false;
    }

    static boolean looksLikeListingPage(Document original, ExtractedJobFields fields) {
        if (original == null) {
            return false;
        }
        if (fields.isFromStructuredData() && notBlank(fields.getTitle()) && notBlank(fields.getDescription())) {
            return false;
        }
        int jobLinks = original.select("a[href]").stream()
                .map(el -> el.attr("abs:href"))
                .filter(href -> href != null && JOB_PATH.matcher(href).find())
                .map(JobContentQualityValidator::stripQuery)
                .distinct()
                .toList()
                .size();
        boolean listingWords = original.text() != null
                && original.text().toLowerCase(Locale.ROOT).contains("search jobs");
        if (jobLinks >= 8) {
            String desc = fields.getDescription() == null ? "" : fields.getDescription().toLowerCase(Locale.ROOT);
            boolean singleJobMarkers = desc.contains("responsibilit")
                    || desc.contains("requirement")
                    || desc.contains("qualification")
                    || desc.contains("about the role");
            return !singleJobMarkers;
        }
        return listingWords && jobLinks >= 5;
    }

    private static String stripQuery(String href) {
        int q = href.indexOf('?');
        return q < 0 ? href : href.substring(0, q);
    }

    private static int markerScore(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String marker : JOB_MARKERS) {
            if (lower.contains(marker)) {
                score++;
            }
        }
        return score;
    }

    private static boolean hasTitleOrCompany(ExtractedJobFields fields) {
        return notBlank(fields.getTitle()) || notBlank(fields.getCompany());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

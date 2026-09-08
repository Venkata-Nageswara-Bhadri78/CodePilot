package com.developer.copilot.jobextraction.automatedjobextraction.extract;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.developer.copilot.jobextraction.automatedjobextraction.exception.InvalidAutomatedJobUrlException;
import com.developer.copilot.jobextraction.automatedjobextraction.fetch.FetchedJobPage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Identifies the target job on a fetched page, runs extraction strategies in priority
 * order, strips unrelated chrome, and produces labeled text for manual extraction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobExtractionPipeline {

    private final List<JobExtractionStrategy> strategies;
    private final HtmlNoiseStripper htmlNoiseStripper;
    private final JobContentQualityValidator qualityValidator;
    private final ExtractedJobTextFormatter formatter;

    public String extractJobText(String canonicalUrl, FetchedJobPage page) {
        if (page == null || page.body() == null || page.body().isBlank()) {
            throw new InvalidAutomatedJobUrlException();
        }
        URI uri = URI.create(canonicalUrl);
        if (isJsonPayload(page.contentType(), page.body())) {
            String jsonWrapped = wrapJsonAsHtml(page.body());
            return extractFromHtml(uri, jsonWrapped, canonicalUrl);
        }
        return extractFromHtml(uri, page.body(), canonicalUrl);
    }

    private String extractFromHtml(URI uri, String html, String canonicalUrl) {
        Document original = Jsoup.parse(html, canonicalUrl);
        Document cleaned = htmlNoiseStripper.strip(original);
        ExtractedJobFields merged = new ExtractedJobFields();
        merged.setSourceUrl(canonicalUrl);

        List<JobExtractionStrategy> ordered = strategies.stream()
                .sorted(Comparator.comparingInt(JobExtractionStrategy::order))
                .toList();
        for (JobExtractionStrategy strategy : ordered) {
            try {
                if (!strategy.supports(uri, original, cleaned)) {
                    continue;
                }
                ExtractedJobFields extracted = strategy.extract(uri, original, cleaned);
                merged = FieldMerger.merge(merged, extracted);
            } catch (RuntimeException ex) {
                log.debug("Extraction strategy {} skipped due to parse error", strategy.getClass().getSimpleName());
            }
        }
        if (merged.getSourceUrl() == null) {
            merged.setSourceUrl(canonicalUrl);
        }
        String formatted = formatter.format(merged);
        if (!qualityValidator.isIdentifiableJob(uri, merged, formatted, original)) {
            throw new InvalidAutomatedJobUrlException();
        }
        return formatted;
    }

    static boolean isJsonPayload(String contentType, String body) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String trimmed = body == null ? "" : body.stripLeading();
        boolean jsonType = type.contains("application/json") || type.contains("application/ld+json");
        if (jsonType) {
            return true;
        }
        boolean objectOrArray = trimmed.startsWith("{") || trimmed.startsWith("[");
        if (!objectOrArray) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.contains("jobposting") || trimmed.contains("jobPostingInfo");
    }

    static String wrapJsonAsHtml(String json) {
        String safe = json.replace("</", "<\\/");
        return "<html><head><script type=\"application/ld+json\">"
                + safe
                + "</script></head><body></body></html>";
    }
}

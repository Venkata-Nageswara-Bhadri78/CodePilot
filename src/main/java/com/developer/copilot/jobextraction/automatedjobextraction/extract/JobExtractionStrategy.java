package com.developer.copilot.jobextraction.automatedjobextraction.extract;

import java.net.URI;

import org.jsoup.nodes.Document;

/**
 * One extraction approach (JSON-LD, ATS markup, semantic HTML, ...). Strategies are
 * independent so a new site family can be added without rewriting the pipeline.
 */
public interface JobExtractionStrategy {

    /**
     * Lower values run first and win on merge conflicts.
     */
    int order();

    boolean supports(URI url, Document original, Document cleaned);

    ExtractedJobFields extract(URI url, Document original, Document cleaned);
}

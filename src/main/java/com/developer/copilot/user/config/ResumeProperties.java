package com.developer.copilot.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "resume")
public class ResumeProperties {

    /**
     * Maximum resumes allowed per user.
     */
    private int maxResumeCount = 10;

    /**
     * Maximum file size in MB.
     */
    private int maxFileSizeMb = 5;

    /**
     * Resume text extraction and parsing settings.
     */
    private Parsing parsing = new Parsing();

    @Getter
    @Setter
    public static class Parsing {

        /**
         * Total parse attempts before a resume is marked permanently failed.
         */
        private int maxAttempts = 3;

        /**
         * Extracted text is truncated beyond this length to bound memory and column size.
         */
        private int maxTextLength = 200_000;

        /**
         * Stamped onto every parsed record. Reads re-parse when this does not match.
         */
        private String parserVersion = "v1";

        /**
         * PDFs with more pages than this are rejected during extraction.
         */
        private int maxPages = 30;

        /**
         * On-demand parse (internal GET) waits at most this long for PDFBox.
         */
        private int timeoutSeconds = 15;
    }
}

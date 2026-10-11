package com.developer.copilot.jobextraction.automatedjobextraction.exception;

import lombok.Getter;

/**
 * AI structuring failed after the page text was already extracted. Carries that text so an
 * automatic save can keep a draft. Preview callers never see this type.
 */
@Getter
public class RecoverableAutomatedExtractionException extends RuntimeException {

    private final String normalizedUrl;
    private final String extractedText;
    private final Long resumeId;

    public RecoverableAutomatedExtractionException(
            String normalizedUrl,
            String extractedText,
            Long resumeId,
            RuntimeException cause) {
        super("Automated extraction could not structure the posting.", cause);
        this.normalizedUrl = normalizedUrl;
        this.extractedText = extractedText;
        this.resumeId = resumeId;
    }

    public RuntimeException aiFailure() {
        Throwable cause = getCause();
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        return this;
    }
}

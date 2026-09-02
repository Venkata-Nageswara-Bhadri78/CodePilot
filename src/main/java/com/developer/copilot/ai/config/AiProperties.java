package com.developer.copilot.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.developer.copilot.ai.dto.request.AiMode;

import lombok.Data;

/**
 * Configuration properties for Copilot AI Service.
 * ChatClient uses {@link #defaultModel} as the single model source of truth.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String provider = "gemini";

    private String defaultModel = "gemini-flash-latest";

    private int timeoutSeconds = 60;

    /** Hard cap on completion tokens for most modes. */
    private int maxCompletionTokens = 2048;

    /** Cover letters may be longer than a match-score blurb. */
    private int coverLetterMaxCompletionTokens = 4096;

    /** Oldest turns are dropped before the provider call. Validation still allows 40 inbound. */
    private int maxPriorTurnsSent = 16;

    public int maxTokensFor(AiMode mode) {
        if (mode == AiMode.COVER_LETTER) {
            return Math.max(1, coverLetterMaxCompletionTokens);
        }
        return Math.max(1, maxCompletionTokens);
    }

    /**
     * @deprecated Prefer {@link #timeoutSeconds}. Kept for backward-compatible property binding.
     */
    @Deprecated
    public void setStreamingTimeoutSeconds(int streamingTimeoutSeconds) {
        this.timeoutSeconds = streamingTimeoutSeconds;
    }

    /**
     * @deprecated Prefer {@link #getTimeoutSeconds()}.
     */
    @Deprecated
    public int getStreamingTimeoutSeconds() {
        return timeoutSeconds;
    }
}

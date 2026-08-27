package com.developer.copilot.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configuration properties for Copilot AI Service.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /**
     * Active AI provider label used in health/metadata responses (e.g. gemini, openai).
     */
    private String provider = "gemini";

    /**
     * Default model identifier applied to ChatClient invocations and response metadata.
     */
    private String defaultModel = "gemini-flash-latest";

    /**
     * Timeout in seconds for all AI provider calls (synchronous and streaming).
     */
    private int timeoutSeconds = 60;

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

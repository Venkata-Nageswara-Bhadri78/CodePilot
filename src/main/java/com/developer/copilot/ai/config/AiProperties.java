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
     * Active AI Provider: gemini, openai, custom-agentic-ai.
     */
    private String provider = "gemini";

    /**
     * Default model identifier (e.g. gemini-flash-latest, gemini-2.0-flash, gpt-4o).
     */
    private String defaultModel = "gemini-flash-latest";

    /**
     * Maximum streaming timeout in seconds.
     */
    private int streamingTimeoutSeconds = 60;

    /**
     * Future dedicated Agentic AI / Private LLM server URL.
     */
    private String agenticServerUrl = "http://localhost:8000/v1";
}

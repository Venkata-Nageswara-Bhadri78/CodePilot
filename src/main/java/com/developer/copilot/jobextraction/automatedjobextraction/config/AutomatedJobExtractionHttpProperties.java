package com.developer.copilot.jobextraction.automatedjobextraction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.developer.copilot.jobextraction.automatedjobextraction.util.AutomatedJobExtractionLimits;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.automatedjobextraction.http")
public class AutomatedJobExtractionHttpProperties {

    private int connectTimeoutMs = AutomatedJobExtractionLimits.DEFAULT_CONNECT_TIMEOUT_MS;

    private int requestTimeoutMs = AutomatedJobExtractionLimits.DEFAULT_REQUEST_TIMEOUT_MS;

    private int maxResponseBytes = AutomatedJobExtractionLimits.DEFAULT_MAX_RESPONSE_BYTES;

    private int maxRedirects = AutomatedJobExtractionLimits.DEFAULT_MAX_REDIRECTS;

    private String userAgent = "CopilotJobExtraction/1.0";
}

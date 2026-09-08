package com.developer.copilot.jobextraction.automatedjobextraction.fetch;

/**
 * A successfully retrieved job page after redirect following.
 */
public record FetchedJobPage(String finalUrl, String contentType, String body) {
}

package com.developer.copilot.jobextraction.automatedjobextraction.fetch;

/**
 * Raw HTTP result from a single hop. {@code body} may be empty for redirects.
 */
public record JobPageHttpResponse(
        int statusCode,
        String location,
        String contentType,
        byte[] body) {
}

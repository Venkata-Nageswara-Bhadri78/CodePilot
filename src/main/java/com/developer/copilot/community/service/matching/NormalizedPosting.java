package com.developer.copilot.community.service.matching;

/**
 * One posting URL after conservative normalization. {@code specific} is false for a host
 * root or a generic listing path, which is not enough to create a community.
 */
public record NormalizedPosting(
        String submittedUrl,
        String storedOriginalUrl,
        String normalizedUrl,
        String hash,
        boolean specific) {
}

package com.developer.copilot.auth.service.oauth;

/**
 * Claims accepted from a verified Google ID token. The raw token is never retained.
 */
public record VerifiedGoogleIdentity(String subject, String email, String name) {
}

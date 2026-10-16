package com.developer.copilot.community.dto;

/**
 * Whether the signed-in user may use the community feature.
 * Comparison is the stored {@code accepted_terms_version} against
 * {@code app.community.terms-version}, exact match after trim.
 */
public enum CommunityConsentState {

    /** No community account. Signup does not create one. */
    TERMS_ACCEPTANCE_REQUIRED,

    /** An account exists, but it accepted an older terms version. */
    TERMS_REACCEPTANCE_REQUIRED,

    /** An account exists and it accepted the current terms version. */
    ACCOUNT_ACTIVE
}

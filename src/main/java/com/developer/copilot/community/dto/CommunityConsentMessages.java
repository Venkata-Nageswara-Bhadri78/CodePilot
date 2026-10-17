package com.developer.copilot.community.dto;

/**
 * Stable client-facing sentences for community consent. Do not put SQL, tokens, or profile data in these.
 */
public final class CommunityConsentMessages {

    public static final String ACCOUNT_REQUIRED =
            "A community account is required. Accept the current terms to create one.";

    public static final String TERMS_CHANGED =
            "The community terms have changed. Accept the current version to continue.";

    public static final String MUST_ACCEPT_FIRST =
            "Community terms must be accepted before an account can be created.";

    public static final String VERSION_MISMATCH =
            "Accept the current community terms version.";

    public static final String CONSENT_REQUIRED =
            "You must accept the community terms.";

    public static final String TERMS_VERSION_REQUIRED =
            "Terms version is required.";

    public static final String TERMS_VERSION_TOO_LONG =
            "Terms version cannot exceed 64 characters.";

    public static final String CREATED = "Community account created.";

    public static final String ACTIVE = "Community account is active.";

    public static final String REACCEPTED = "Community terms accepted.";

    public static final String UNKNOWN_FIELD =
            "Send only accepted and termsVersion. A user id is not accepted.";

    private CommunityConsentMessages() {
    }

    public static String forState(CommunityConsentState state) {
        return switch (state) {
            case TERMS_ACCEPTANCE_REQUIRED -> MUST_ACCEPT_FIRST;
            case TERMS_REACCEPTANCE_REQUIRED -> TERMS_CHANGED;
            case ACCOUNT_ACTIVE -> ACTIVE;
        };
    }
}

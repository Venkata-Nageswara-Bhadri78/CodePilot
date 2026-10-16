package com.developer.copilot.community.dto.response;

import com.developer.copilot.community.dto.CommunityConsentState;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Consent state for the authenticated user. "
        + "account is null when no community account exists.")
public class CommunityConsentResponse {

    @Schema(description = "TERMS_ACCEPTANCE_REQUIRED, TERMS_REACCEPTANCE_REQUIRED, or ACCOUNT_ACTIVE.",
            example = "TERMS_ACCEPTANCE_REQUIRED")
    private CommunityConsentState state;

    @Schema(description = "True when a community account row exists, even if its terms version is stale.",
            example = "false")
    private boolean accountPresent;

    @Schema(description = "True when the user must accept the current terms before using the community feature.",
            example = "true")
    private boolean termsAcceptanceRequired;

    @Schema(description = "Configured terms version the client must submit to accept.", example = "1")
    private String currentTermsVersion;

    @Schema(description = "Present when an account row exists. Null when the user has never accepted.")
    private CommunityAccountResponse account;
}

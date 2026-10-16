package com.developer.copilot.community.dto.request;

import com.developer.copilot.community.dto.CommunityConsentMessages;
import com.developer.copilot.community.exception.CommunityConsentRejectedException;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(description = "Explicit acceptance of the current community terms. "
        + "The account owner is the authenticated user. Do not send a user id.")
public class AcceptCommunityTermsRequest {

    @Schema(description = "Must be true. A missing or false value is not consent.", example = "true")
    @NotNull(message = CommunityConsentMessages.CONSENT_REQUIRED)
    @AssertTrue(message = CommunityConsentMessages.CONSENT_REQUIRED)
    private Boolean accepted;

    @Schema(description = "Must equal the currentTermsVersion from GET /api/v1/community/account.",
            example = "1",
            maxLength = 64)
    @NotBlank(message = CommunityConsentMessages.TERMS_VERSION_REQUIRED)
    @Size(max = 64, message = CommunityConsentMessages.TERMS_VERSION_TOO_LONG)
    private String termsVersion;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object ignored) {
        throw new CommunityConsentRejectedException(CommunityConsentMessages.UNKNOWN_FIELD);
    }
}

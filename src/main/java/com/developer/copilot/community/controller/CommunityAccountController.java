package com.developer.copilot.community.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.community.dto.CommunityConsentMessages;
import com.developer.copilot.community.dto.request.AcceptCommunityTermsRequest;
import com.developer.copilot.community.dto.response.CommunityConsentResponse;
import com.developer.copilot.community.service.CommunityAccountAcceptance;
import com.developer.copilot.community.service.CommunityAccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Community account",
        description = "Opt-in community account for the signed-in user. "
                + "Registration does not create this account. "
                + "Accept the current terms version explicitly. A user id in the body is rejected.")
@RestController
@RequestMapping("/api/v1/community/account")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class CommunityAccountController {

    private final CommunityAccountService communityAccountService;

    @Operation(summary = "Read community consent state",
            description = "Does not create an account. "
                    + "TERMS_ACCEPTANCE_REQUIRED means there is no account. "
                    + "TERMS_REACCEPTANCE_REQUIRED means an account exists for an older terms version. "
                    + "ACCOUNT_ACTIVE means the stored version matches app.community.terms-version.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Consent state"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Extension client or incomplete username",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<CommunityConsentResponse>> currentConsent() {
        CommunityConsentResponse consent = communityAccountService.currentConsent();
        return ResponseEntity.ok(envelope(CommunityConsentMessages.forState(consent.getState()), consent));
    }

    @Operation(summary = "Accept the current community terms",
            description = "Creates the account when the user has none. "
                    + "Repeating the same current version returns the existing account. "
                    + "A newer configured version updates the same row. "
                    + "accepted must be true and termsVersion must equal the configured current version.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Existing account returned or terms version refreshed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Missing consent, wrong terms version, or unknown field",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Accept the current community terms version.\",\"data\":null,\"timestamp\":\"2026-10-15T16:40:00\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Extension client or incomplete username",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<CommunityConsentResponse>> acceptCurrentTerms(
            @Valid @RequestBody AcceptCommunityTermsRequest request) {
        CommunityAccountAcceptance acceptance = communityAccountService.acceptCurrentTerms(request);
        HttpStatus status = acceptance.getKind() == CommunityAccountAcceptance.Kind.CREATED
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        String message = switch (acceptance.getKind()) {
            case CREATED -> CommunityConsentMessages.CREATED;
            case REACCEPTED -> CommunityConsentMessages.REACCEPTED;
            case UNCHANGED -> CommunityConsentMessages.ACTIVE;
        };
        return ResponseEntity.status(status).body(envelope(message, acceptance.getConsent()));
    }

    private static ApiResponse<CommunityConsentResponse> envelope(String message, CommunityConsentResponse consent) {
        return ApiResponse.<CommunityConsentResponse>builder()
                .success(true)
                .message(message)
                .data(consent)
                .timestamp(LocalDateTime.now())
                .build();
    }
}

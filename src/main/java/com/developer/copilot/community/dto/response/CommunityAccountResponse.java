package com.developer.copilot.community.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Community account consent record. Profile and credential fields are not included.")
public class CommunityAccountResponse {

    @Schema(description = "Community account id.", example = "10")
    private Long id;

    @Schema(description = "Terms version stored on this account.", example = "1")
    private String acceptedTermsVersion;

    @Schema(description = "When the stored terms version was accepted.", example = "2026-10-15T16:40:00")
    private LocalDateTime termsAcceptedAt;

    @Schema(description = "When this account row was first created.", example = "2026-10-15T16:40:00")
    private LocalDateTime createdAt;
}

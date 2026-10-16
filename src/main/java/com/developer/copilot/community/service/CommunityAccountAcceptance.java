package com.developer.copilot.community.service;

import com.developer.copilot.community.dto.response.CommunityConsentResponse;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class CommunityAccountAcceptance {

    public enum Kind {
        CREATED,
        UNCHANGED,
        REACCEPTED
    }

    private final CommunityConsentResponse consent;
    private final Kind kind;
}

package com.developer.copilot.community.websocket;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.community.config.CommunityAccountProperties;
import com.developer.copilot.community.dto.CommunityConsentMessages;
import com.developer.copilot.community.entity.CommunityAccount;
import com.developer.copilot.community.entity.CommunityMembership;
import com.developer.copilot.community.entity.MembershipStatus;
import com.developer.copilot.community.exception.CommunityAccountRequiredException;
import com.developer.copilot.community.exception.CommunityMembershipRequiredException;
import com.developer.copilot.community.exception.CommunityUnavailableException;
import com.developer.copilot.community.exception.CommunityValidationException;
import com.developer.copilot.community.exception.TermsAcceptanceRequiredException;
import com.developer.copilot.community.repository.CommunityAccountRepository;
import com.developer.copilot.community.repository.CommunityMembershipRepository;
import com.developer.copilot.community.repository.CommunityRepository;
import com.developer.copilot.community.service.CommunityMessages;

import lombok.RequiredArgsConstructor;

/**
 * Same gate as {@code requireActiveMembership}, addressed by user id for a socket that is already authenticated.
 */
@Service
@RequiredArgsConstructor
public class CommunityRealtimeAccess {

    private final CommunityAccountRepository communityAccountRepository;
    private final CommunityAccountProperties communityAccountProperties;
    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository communityMembershipRepository;

    public void requireParticipant(Long userId, Long communityId) {
        if (userId == null) {
            throw new CommunityAccountRequiredException(CommunityConsentMessages.ACCOUNT_REQUIRED);
        }
        if (communityId == null || communityId <= 0) {
            throw new CommunityValidationException(CommunityMessages.INVALID_COMMUNITY_ID);
        }
        CommunityAccount account = communityAccountRepository.findByUserId(userId).orElse(null);
        if (account == null) {
            throw new CommunityAccountRequiredException(CommunityConsentMessages.ACCOUNT_REQUIRED);
        }
        if (!communityAccountProperties.currentTermsVersion().equals(account.getAcceptedTermsVersion())) {
            throw new TermsAcceptanceRequiredException(CommunityConsentMessages.TERMS_CHANGED);
        }
        if (!communityRepository.existsById(communityId)) {
            throw new CommunityUnavailableException(CommunityMessages.COMMUNITY_UNAVAILABLE);
        }
        CommunityMembership membership = communityMembershipRepository
                .findByCommunityIdAndUserId(communityId, userId)
                .orElse(null);
        if (membership == null || membership.getMembershipStatus() != MembershipStatus.ACTIVE) {
            throw new CommunityMembershipRequiredException(CommunityMessages.MEMBERSHIP_REQUIRED);
        }
    }

    public static Long userId(Authentication authentication) {
        User user = user(authentication);
        return user == null ? null : user.getId();
    }

    public static User user(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof com.developer.copilot.auth.security.CustomUserDetails details)) {
            return null;
        }
        return details.getUser();
    }
}

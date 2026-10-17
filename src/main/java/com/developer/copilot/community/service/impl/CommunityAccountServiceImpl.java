package com.developer.copilot.community.service.impl;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.common.security.CurrentUserService;
import com.developer.copilot.community.config.CommunityAccountProperties;
import com.developer.copilot.community.dto.CommunityConsentMessages;
import com.developer.copilot.community.dto.CommunityConsentState;
import com.developer.copilot.community.dto.request.AcceptCommunityTermsRequest;
import com.developer.copilot.community.dto.response.CommunityAccountResponse;
import com.developer.copilot.community.dto.response.CommunityConsentResponse;
import com.developer.copilot.community.entity.CommunityAccount;
import com.developer.copilot.community.exception.CommunityAccountRequiredException;
import com.developer.copilot.community.exception.CommunityConsentRejectedException;
import com.developer.copilot.community.exception.CommunityTermsVersionException;
import com.developer.copilot.community.exception.TermsAcceptanceRequiredException;
import com.developer.copilot.community.repository.CommunityAccountRepository;
import com.developer.copilot.community.service.CommunityAccountAcceptance;
import com.developer.copilot.community.service.CommunityAccountService;
import com.developer.copilot.community.service.CommunityAccountWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityAccountServiceImpl implements CommunityAccountService {

    private final CommunityAccountRepository communityAccountRepository;
    private final CommunityAccountWriter communityAccountWriter;
    private final CurrentUserService currentUserService;
    private final CommunityAccountProperties communityAccountProperties;

    @Override
    @Transactional(readOnly = true)
    public CommunityConsentResponse currentConsent() {
        User user = currentUserService.getCurrentUser();
        CommunityAccount account = communityAccountRepository.findByUserId(user.getId()).orElse(null);
        return toResponse(account);
    }

    @Override
    public CommunityAccountAcceptance acceptCurrentTerms(AcceptCommunityTermsRequest request) {
        String current = submittedCurrentVersion(request);
        User user = currentUserService.getCurrentUser();
        CommunityAccount existing = communityAccountRepository.findByUserId(user.getId()).orElse(null);
        if (existing != null) {
            return acceptanceForExisting(existing, current, user.getId());
        }
        try {
            CommunityAccount created = communityAccountWriter.create(user, current);
            log.info("Created community account id={} for userId={}", created.getId(), user.getId());
            return new CommunityAccountAcceptance(toResponse(created), CommunityAccountAcceptance.Kind.CREATED);
        } catch (DataIntegrityViolationException ex) {
            CommunityAccount winner = communityAccountRepository.findByUserId(user.getId()).orElse(null);
            if (winner == null || !CommunityAccountWriter.isUniqueUserViolation(ex)) {
                throw ex;
            }
            log.info("Community account already exists for userId={}", user.getId());
            return acceptanceForExisting(winner, current, user.getId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityAccount requireActiveAccount() {
        User user = currentUserService.getCurrentUser();
        CommunityAccount account = communityAccountRepository.findByUserId(user.getId()).orElse(null);
        if (account == null) {
            throw new CommunityAccountRequiredException(CommunityConsentMessages.ACCOUNT_REQUIRED);
        }
        if (!communityAccountProperties.currentTermsVersion().equals(account.getAcceptedTermsVersion())) {
            throw new TermsAcceptanceRequiredException(CommunityConsentMessages.TERMS_CHANGED);
        }
        return account;
    }

    private CommunityAccountAcceptance acceptanceForExisting(
            CommunityAccount existing,
            String current,
            Long userId) {
        if (current.equals(existing.getAcceptedTermsVersion())) {
            return new CommunityAccountAcceptance(toResponse(existing), CommunityAccountAcceptance.Kind.UNCHANGED);
        }
        CommunityAccount updated = communityAccountWriter.reaccept(existing.getId(), current);
        log.info("Recorded community terms version {} for userId={}", current, userId);
        return new CommunityAccountAcceptance(toResponse(updated), CommunityAccountAcceptance.Kind.REACCEPTED);
    }

    private String submittedCurrentVersion(AcceptCommunityTermsRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.getAccepted())) {
            throw new CommunityConsentRejectedException(CommunityConsentMessages.CONSENT_REQUIRED);
        }
        if (request.getTermsVersion() == null || request.getTermsVersion().isBlank()) {
            throw new CommunityConsentRejectedException(CommunityConsentMessages.TERMS_VERSION_REQUIRED);
        }
        String submitted = request.getTermsVersion().trim();
        if (submitted.length() > 64) {
            throw new CommunityConsentRejectedException(CommunityConsentMessages.TERMS_VERSION_TOO_LONG);
        }
        String current = communityAccountProperties.currentTermsVersion();
        if (!current.equals(submitted)) {
            throw new CommunityTermsVersionException(CommunityConsentMessages.VERSION_MISMATCH);
        }
        return current;
    }

    private CommunityConsentResponse toResponse(CommunityAccount account) {
        String current = communityAccountProperties.currentTermsVersion();
        if (account == null) {
            return CommunityConsentResponse.builder()
                    .state(CommunityConsentState.TERMS_ACCEPTANCE_REQUIRED)
                    .accountPresent(false)
                    .termsAcceptanceRequired(true)
                    .currentTermsVersion(current)
                    .account(null)
                    .build();
        }
        boolean currentConsent = current.equals(account.getAcceptedTermsVersion());
        return CommunityConsentResponse.builder()
                .state(currentConsent
                        ? CommunityConsentState.ACCOUNT_ACTIVE
                        : CommunityConsentState.TERMS_REACCEPTANCE_REQUIRED)
                .accountPresent(true)
                .termsAcceptanceRequired(!currentConsent)
                .currentTermsVersion(current)
                .account(CommunityAccountResponse.builder()
                        .id(account.getId())
                        .acceptedTermsVersion(account.getAcceptedTermsVersion())
                        .termsAcceptedAt(account.getTermsAcceptedAt())
                        .createdAt(account.getCreatedAt())
                        .build())
                .build();
    }
}

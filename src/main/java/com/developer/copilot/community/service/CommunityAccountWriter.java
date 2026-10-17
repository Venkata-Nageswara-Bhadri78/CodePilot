package com.developer.copilot.community.service;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.community.entity.CommunityAccount;
import com.developer.copilot.community.repository.CommunityAccountRepository;

import lombok.RequiredArgsConstructor;

/**
 * Writes the one community-account row. Each method runs in its own transaction so a
 * uniqueness failure rolls back only the losing insert and the caller can read the winner.
 */
@Service
@RequiredArgsConstructor
public class CommunityAccountWriter {

    private final CommunityAccountRepository communityAccountRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CommunityAccount create(User user, String termsVersion) {
        CommunityAccount account = new CommunityAccount();
        account.setUser(user);
        account.setAcceptedTermsVersion(termsVersion);
        account.setTermsAcceptedAt(LocalDateTime.now());
        return communityAccountRepository.saveAndFlush(account);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CommunityAccount reaccept(Long accountId, String termsVersion) {
        CommunityAccount account = communityAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException(
                        "Community account disappeared before terms could be saved."));
        if (termsVersion.equals(account.getAcceptedTermsVersion())) {
            return account;
        }
        account.setAcceptedTermsVersion(termsVersion);
        account.setTermsAcceptedAt(LocalDateTime.now());
        return communityAccountRepository.saveAndFlush(account);
    }

    public static boolean isUniqueUserViolation(DataIntegrityViolationException ex) {
        StringBuilder text = new StringBuilder();
        Throwable current = ex;
        while (current != null && text.length() < 4000) {
            if (current.getMessage() != null) {
                text.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return text.toString().toLowerCase(java.util.Locale.ROOT).contains("uk_community_account_user");
    }
}

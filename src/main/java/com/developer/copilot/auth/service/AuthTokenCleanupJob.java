package com.developer.copilot.auth.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.developer.copilot.auth.repository.EmailVerificationRepository;
import com.developer.copilot.auth.repository.PasswordResetTokenRepository;
import com.developer.copilot.auth.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final Clock clock;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredRows() {
        LocalDateTime now = LocalDateTime.now(clock);
        int refresh = refreshTokenRepository.deleteRevokedOrExpired(now);
        int resets = passwordResetTokenRepository.deleteUsedOrExpired(now);
        int otps = emailVerificationRepository.deleteExpiredOrVerified(now);
        if (refresh + resets + otps > 0) {
            log.info("Purged auth rows refresh={}, resets={}, otps={}", refresh, resets, otps);
        }
    }
}

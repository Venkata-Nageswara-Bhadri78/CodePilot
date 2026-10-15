package com.developer.copilot.auth.service.oauth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.developer.copilot.auth.config.AuthProperties;
import com.developer.copilot.auth.dto.GoogleAuthRequest;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.ratelimit.service.AuthRateLimitService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuth2Service {

    private final GoogleIdTokenService googleIdTokenService;
    private final GoogleAccountWriter googleAccountWriter;
    private final AuthRateLimitService authRateLimitService;
    private final AuthProperties authProperties;

    /**
     * Verifies the Google ID token outside a database transaction, then resolves the user.
     */
    public User authenticate(GoogleAuthRequest request) {
        VerifiedGoogleIdentity identity = googleIdTokenService.verify(request.getIdToken());
        authRateLimitService.consumeOrThrow(
                "google-email",
                identity.email(),
                authProperties.getGoogleRateLimitPerMinute(),
                60);
        try {
            User user = googleAccountWriter.loginOrCreate(identity);
            log.info("Google sign-in succeeded for userId={} provider=GOOGLE", user.getId());
            return user;
        } catch (DataIntegrityViolationException ex) {
            log.debug("Google sign-in hit a unique constraint; re-reading identity");
            User user = googleAccountWriter.recover(identity);
            log.info("Google sign-in succeeded for userId={} provider=GOOGLE", user.getId());
            return user;
        }
    }

    /**
     * Links a verified Google identity to an existing signed-in user. The public sign-in
     * path never calls this. Email equality is checked in the writer together with the token.
     */
    public void link(GoogleAuthRequest request, Long userId) {
        VerifiedGoogleIdentity identity = googleIdTokenService.verify(request.getIdToken());
        authRateLimitService.consumeOrThrow(
                "google-link",
                String.valueOf(userId),
                authProperties.getGoogleLinkRateLimitPerMinute(),
                60);
        try {
            googleAccountWriter.link(userId, identity);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Google link hit a unique constraint; re-reading identity");
            googleAccountWriter.linkAfterConflict(userId, identity);
        }
        log.info("Google account linked for userId={} provider=GOOGLE", userId);
    }
}

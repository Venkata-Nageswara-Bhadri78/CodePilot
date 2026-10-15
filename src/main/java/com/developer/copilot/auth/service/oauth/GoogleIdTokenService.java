package com.developer.copilot.auth.service.oauth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.developer.copilot.auth.config.GoogleProperties;
import com.developer.copilot.auth.exception.InvalidGoogleTokenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleIdTokenService {

    static final int MAX_TOKEN_LENGTH = 4096;

    private static final Set<String> ISSUERS = Set.of(
            "accounts.google.com",
            "https://accounts.google.com");

    private final GoogleIdTokenVerifierAdapter verifierAdapter;
    private final GoogleProperties googleProperties;
    private final Clock clock;

    public VerifiedGoogleIdentity verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_TOKEN_LENGTH) {
            throw new InvalidGoogleTokenException();
        }
        if (googleProperties.resolvedClientIds().isEmpty()) {
            log.debug("Google ID token rejected because app.google.client-ids is empty");
            throw new InvalidGoogleTokenException();
        }

        GoogleIdToken token;
        try {
            token = verifierAdapter.verify(rawToken);
        } catch (GeneralSecurityException | IOException | RuntimeException ex) {
            log.debug("Google ID token verification failed: {}", ex.getClass().getSimpleName());
            throw new InvalidGoogleTokenException();
        }
        if (token == null || token.getPayload() == null) {
            throw new InvalidGoogleTokenException();
        }
        return toIdentity(token.getPayload());
    }

    private VerifiedGoogleIdentity toIdentity(GoogleIdToken.Payload payload) {
        if (!ISSUERS.contains(payload.getIssuer())) {
            throw new InvalidGoogleTokenException();
        }
        if (!audienceAllowed(payload.getAudience())) {
            throw new InvalidGoogleTokenException();
        }
        Long expiresAt = payload.getExpirationTimeSeconds();
        if (expiresAt == null || expiresAt <= clock.instant().getEpochSecond()) {
            throw new InvalidGoogleTokenException();
        }

        String subject = payload.getSubject() == null ? "" : payload.getSubject().trim();
        if (subject.isEmpty() || subject.length() > 255) {
            throw new InvalidGoogleTokenException();
        }

        String email = normalizeEmail(payload.getEmail());
        if (email == null || email.isBlank() || email.length() > 255 || !email.contains("@")) {
            throw new InvalidGoogleTokenException();
        }
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new InvalidGoogleTokenException();
        }

        String hostedDomain = googleProperties.resolvedHostedDomain();
        if (!hostedDomain.isEmpty()) {
            String claim = payload.getHostedDomain() == null
                    ? ""
                    : payload.getHostedDomain().trim().toLowerCase(Locale.ROOT);
            if (!hostedDomain.equals(claim)) {
                throw new InvalidGoogleTokenException();
            }
        }

        Object nameClaim = payload.get("name");
        String name = nameClaim instanceof String value ? value : null;
        return new VerifiedGoogleIdentity(subject, email, name);
    }

    private boolean audienceAllowed(Object audience) {
        var allowed = googleProperties.resolvedClientIds();
        if (audience instanceof String value) {
            return allowed.contains(value);
        }
        if (audience instanceof Collection<?> values) {
            for (Object value : values) {
                if (value != null && allowed.contains(value.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

package com.developer.copilot.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Live-user onboarding marker. Granted by {@code JwtAuthenticationFilter} when {@code username}
 * is null. It is not a JWT claim.
 */
public final class AuthOnboardingAuthorities {

    public static final String INCOMPLETE = "ONBOARDING_INCOMPLETE";

    public static final String INCOMPLETE_MESSAGE = "Complete your username to continue.";

    private AuthOnboardingAuthorities() {
    }

    public static boolean isIncomplete(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(INCOMPLETE::equals);
    }
}

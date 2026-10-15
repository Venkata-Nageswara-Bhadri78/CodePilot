package com.developer.copilot.auth.exception;

import com.developer.copilot.auth.security.AuthOnboardingAuthorities;

public class OnboardingIncompleteException extends RuntimeException {

    public OnboardingIncompleteException() {
        super(AuthOnboardingAuthorities.INCOMPLETE_MESSAGE);
    }
}

package com.developer.copilot.auth.service.oauth;

import java.io.IOException;
import java.security.GeneralSecurityException;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

/**
 * Boundary around {@link com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier}
 * so tests can supply tokens without calling Google.
 */
public interface GoogleIdTokenVerifierAdapter {

    GoogleIdToken verify(String idToken) throws GeneralSecurityException, IOException;
}

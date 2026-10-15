package com.developer.copilot.auth.service.oauth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.springframework.stereotype.Component;

import com.developer.copilot.auth.config.GoogleProperties;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import jakarta.annotation.PostConstruct;

@Component
public class LibraryGoogleIdTokenVerifierAdapter implements GoogleIdTokenVerifierAdapter {

    private final GoogleProperties googleProperties;
    private GoogleIdTokenVerifier verifier;

    public LibraryGoogleIdTokenVerifierAdapter(GoogleProperties googleProperties) {
        this.googleProperties = googleProperties;
    }

    @PostConstruct
    void init() {
        List<String> audiences = googleProperties.resolvedClientIds();
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(audiences)
                .build();
    }

    @Override
    public GoogleIdToken verify(String idToken) throws GeneralSecurityException, IOException {
        return verifier.verify(idToken);
    }
}

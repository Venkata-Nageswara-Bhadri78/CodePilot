package com.developer.copilot.auth.service.oauth;

import com.developer.copilot.auth.config.GoogleProperties;
import com.developer.copilot.auth.exception.InvalidGoogleTokenException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.json.webtoken.JsonWebSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleIdTokenServiceTest {

    private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";

    @Mock
    private GoogleIdTokenVerifierAdapter adapter;

    private GoogleProperties properties;
    private Clock clock;
    private GoogleIdTokenService service;

    @BeforeEach
    void setUp() {
        properties = new GoogleProperties();
        properties.setClientIds(List.of(CLIENT_ID));
        clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC);
        service = new GoogleIdTokenService(adapter, properties, clock);
    }

    @Test
    void verify_validToken_returnsNormalizedIdentity() throws Exception {
        when(adapter.verify("raw-token")).thenReturn(token(payload -> { }));

        VerifiedGoogleIdentity identity = service.verify("raw-token");

        assertEquals("google-sub", identity.subject());
        assertEquals("ada@example.com", identity.email());
        assertEquals("Ada Lovelace", identity.name());
    }

    @Test
    void verify_nullFromAdapter_isInvalid() throws Exception {
        when(adapter.verify("raw-token")).thenReturn(null);

        assertThrows(InvalidGoogleTokenException.class, () -> service.verify("raw-token"));
    }

    @Test
    void verify_badSignature_isInvalid() throws Exception {
        when(adapter.verify("raw-token")).thenThrow(new GeneralSecurityException("bad signature"));

        assertThrows(InvalidGoogleTokenException.class, () -> service.verify("raw-token"));
    }

    @Test
    void verify_ioFailure_isInvalid() throws Exception {
        when(adapter.verify("raw-token")).thenThrow(new IOException("keys unavailable"));

        assertThrows(InvalidGoogleTokenException.class, () -> service.verify("raw-token"));
    }

    @Test
    void verify_expired_isInvalid() throws Exception {
        when(adapter.verify("raw-token")).thenReturn(token(payload ->
                payload.setExpirationTimeSeconds(clock.instant().getEpochSecond() - 5)));

        assertThrows(InvalidGoogleTokenException.class, () -> service.verify("raw-token"));
    }

    @Test
    void verify_wrongAudience_isInvalid() throws Exception {
        when(adapter.verify("raw-token")).thenReturn(token(payload -> payload.setAudience("other-client")));

        assertThrows(InvalidGoogleTokenException.class, () -> service.verify("raw-token"));
    }

    @Test
    void verify_wrongIssuer_isInvalid() throws Exception {
        when(adapter.verify("raw-token")).thenReturn(token(payload -> payload.setIssuer("https://evil.example")));

        assertThrows(InvalidGoogleTokenException.class, () -> service.verify("raw-token"));
    }

    @Test
    void verify_missingSubject_isInvalid() throws Exception {
        when(adapter.verify("raw-token")).thenReturn(token(payload -> payload.setSubject(" ")));

        assertThrows(InvalidGoogleTokenException.class, () -> service.verify("raw-token"));
    }

    @Test
    void verify_unverifiedEmail_isInvalid() throws Exception {
        when(adapter.verify("raw-token")).thenReturn(token(payload -> payload.setEmailVerified(false)));

        assertThrows(InvalidGoogleTokenException.class, () -> service.verify("raw-token"));
    }

    @Test
    void verify_emptyClientIds_doesNotCallGoogle() throws Exception {
        properties.setClientIds(List.of());

        assertThrows(InvalidGoogleTokenException.class, () -> service.verify("raw-token"));
        verify(adapter, never()).verify("raw-token");
    }

    @Test
    void verify_hostedDomainMismatch_isInvalid() throws Exception {
        properties.setHostedDomain("example.com");
        when(adapter.verify("raw-token")).thenReturn(token(payload -> payload.setHostedDomain("other.com")));

        assertThrows(InvalidGoogleTokenException.class, () -> service.verify("raw-token"));
    }

    private GoogleIdToken token(Consumer<GoogleIdToken.Payload> customizer) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setIssuer("https://accounts.google.com");
        payload.setSubject("google-sub");
        payload.setAudience(CLIENT_ID);
        payload.setExpirationTimeSeconds(clock.instant().getEpochSecond() + 600);
        payload.setEmail("Ada@Example.com");
        payload.setEmailVerified(true);
        payload.set("name", "Ada Lovelace");
        customizer.accept(payload);
        JsonWebSignature.Header header = new JsonWebSignature.Header();
        header.setAlgorithm("RS256");
        return new GoogleIdToken(header, payload, new byte[] {1}, new byte[] {1});
    }
}

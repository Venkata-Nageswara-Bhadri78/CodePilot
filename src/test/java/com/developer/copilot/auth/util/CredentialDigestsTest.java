package com.developer.copilot.auth.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialDigestsTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256";

    @Test
    void sha256_isStableHexLength64() {
        String digest = CredentialDigests.sha256("token");
        assertEquals(64, digest.length());
        assertEquals(digest, CredentialDigests.sha256("token"));
    }

    @Test
    void matches_isTrueOnlyForSameRawValue() {
        String stored = CredentialDigests.sha256("uuid-value");
        assertTrue(CredentialDigests.matches("uuid-value", stored));
        assertFalse(CredentialDigests.matches("other", stored));
        assertFalse(CredentialDigests.matches(null, stored));
        assertFalse(CredentialDigests.matches("uuid-value", null));
    }

    @Test
    void hmacSha256_isNotPublicSha256() {
        String hmac = CredentialDigests.hmacSha256("123456", SECRET);
        assertEquals(64, hmac.length());
        assertNotEquals(CredentialDigests.sha256("123456"), hmac);
        assertTrue(CredentialDigests.hmacMatches("123456", hmac, SECRET));
        assertFalse(CredentialDigests.hmacMatches("000000", hmac, SECRET));
        assertFalse(CredentialDigests.hmacMatches("123456", hmac, "other-secret-key-that-is-long-enough"));
    }
}

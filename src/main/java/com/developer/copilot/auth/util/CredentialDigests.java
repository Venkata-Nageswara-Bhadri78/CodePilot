package com.developer.copilot.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CredentialDigests {

    private CredentialDigests() {
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for credential storage.", ex);
        }
    }

    public static boolean matches(String rawValue, String storedDigest) {
        if (rawValue == null || storedDigest == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sha256(rawValue).getBytes(StandardCharsets.UTF_8),
                storedDigest.getBytes(StandardCharsets.UTF_8));
    }
}

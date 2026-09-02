package com.developer.copilot.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class CredentialDigests {

    private static final String HMAC_SHA256 = "HmacSHA256";

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

    public static String hmacSha256(String value, String secret) {
        if (value == null || secret == null) {
            throw new IllegalArgumentException("HMAC value and secret are required.");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC-SHA256 is required for OTP storage.", ex);
        }
    }

    public static boolean hmacMatches(String rawValue, String storedDigest, String secret) {
        if (rawValue == null || storedDigest == null || secret == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hmacSha256(rawValue, secret).getBytes(StandardCharsets.UTF_8),
                storedDigest.getBytes(StandardCharsets.UTF_8));
    }
}

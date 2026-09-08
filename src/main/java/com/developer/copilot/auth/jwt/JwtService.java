package com.developer.copilot.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.developer.copilot.auth.config.ExtensionProperties;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.security.AuthClientAuthorities;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

@Service
public class JwtService {

    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.auth.access-expiry-ms:900000}")
    private long expiration;

    @Autowired(required = false)
    private ExtensionProperties extensionProperties;

    @PostConstruct
    void validateConfiguration() {
        if (secret == null || secret.isBlank() || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_SECRET_LENGTH + " characters.");
        }
        if (looksLikePlaceholder(secret)) {
            throw new IllegalStateException(
                    "app.jwt.secret looks like a placeholder. Set APP_JWT_SECRET to a long random value.");
        }
        if (expiration <= 0) {
            throw new IllegalStateException("app.auth.access-expiry-ms must be a positive duration in milliseconds.");
        }
        if (extensionProperties != null) {
            if (extensionProperties.getAccessExpiryMs() <= 0) {
                throw new IllegalStateException(
                        "app.extension.access-expiry-ms must be a positive duration in milliseconds.");
            }
            extensionProperties.resolvedOrigin();
        }
    }

    static boolean looksLikePlaceholder(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return lower.isBlank()
                || lower.contains("enter-your-jwt")
                || lower.contains("your-jwt-configuration")
                || "changeme".equals(lower);
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        return buildToken(user, expiration, null);
    }

    /**
     * Issues an access JWT for the browser-extension client. Same user identity as
     * {@link #generateToken(User)}, plus a signed {@code cid=browser-extension} claim.
     * No refresh token is attached to this JWT.
     */
    public String generateExtensionToken(User user) {
        if (!isExtensionEnabled()) {
            throw new IllegalStateException("Browser extension client is disabled.");
        }
        long extensionExpiry = extensionProperties == null ? expiration : extensionProperties.getAccessExpiryMs();
        return buildToken(user, extensionExpiry, AuthClientAuthorities.CLIENT_ID_BROWSER_EXTENSION);
    }

    private String buildToken(User user, long lifetimeMs, String clientId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + lifetimeMs);

        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("tv", tokenVersion(user))
                .issuedAt(now)
                .expiration(expiry);

        if (clientId != null) {
            builder.claim(AuthClientAuthorities.CLAIM, clientId);
        }

        return builder.signWith(getSigningKey()).compact();
    }

    public Long extractUserId(String token) {
        return Long.parseLong(extractClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return extractClaims(token).get("email", String.class);
    }

    public String extractClientId(String token) {
        return extractClaims(token).get(AuthClientAuthorities.CLAIM, String.class);
    }

    public boolean isBrowserExtensionClient(String token) {
        return AuthClientAuthorities.isBrowserExtensionClientId(extractClientId(token));
    }

    public boolean isTokenValid(String token, User user) {
        Claims claims = extractClaims(token);
        Integer tokenVersion = claims.get("tv", Integer.class);
        if (tokenVersion == null) {
            tokenVersion = 0;
        }

        if (!isTrustedClientClaim(claims.get(AuthClientAuthorities.CLAIM, String.class))) {
            return false;
        }

        return extractUserId(token).equals(user.getId())
                && !claims.getExpiration().before(new Date())
                && tokenVersion.equals(tokenVersion(user));
    }

    /**
     * Missing {@code cid} is the web frontend (existing tokens). Only
     * {@code browser-extension} is accepted as an additional client. Unknown values are rejected
     * so a forged/unrecognized client claim cannot inherit web privileges.
     */
    private boolean isTrustedClientClaim(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return true;
        }
        if (!AuthClientAuthorities.isBrowserExtensionClientId(clientId)) {
            return false;
        }
        return isExtensionEnabled();
    }

    private boolean isExtensionEnabled() {
        return extensionProperties == null || extensionProperties.isEnabled();
    }

    private int tokenVersion(User user) {
        return user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }

    private Claims extractClaims(String token) {

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

    }

}

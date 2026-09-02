package com.developer.copilot.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.developer.copilot.auth.entity.User;

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

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("tv", tokenVersion(user))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public Long extractUserId(String token) {
        return Long.parseLong(extractClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return extractClaims(token).get("email", String.class);
    }

    public boolean isTokenValid(String token, User user) {
        Claims claims = extractClaims(token);
        Integer tokenVersion = claims.get("tv", Integer.class);
        if (tokenVersion == null) {
            tokenVersion = 0;
        }

        return extractUserId(token).equals(user.getId())
                && !claims.getExpiration().before(new Date())
                && tokenVersion.equals(tokenVersion(user));
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

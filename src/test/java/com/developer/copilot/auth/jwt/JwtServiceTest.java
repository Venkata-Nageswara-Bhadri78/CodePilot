package com.developer.copilot.auth.jwt;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.enums.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService();

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secret", "test-secret-key-that-is-long-enough-for-hmac-sha256");
        ReflectionTestUtils.setField(jwtService, "expiration", 3_600_000L);

        user = new User();
        user.setId(42L);
        user.setEmail("john@example.com");
        user.setRole(Role.USER);
        user.setTokenVersion(0);
    }

    @Test
    void generateToken_containsUserIdAndEmail() {
        String token = jwtService.generateToken(user);

        assertEquals(42L, jwtService.extractUserId(token));
        assertEquals("john@example.com", jwtService.extractEmail(token));
    }

    @Test
    void isTokenValid_returnsTrueForMatchingUser() {
        String token = jwtService.generateToken(user);

        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void isTokenValid_returnsFalseForDifferentUser() {
        String token = jwtService.generateToken(user);

        User otherUser = new User();
        otherUser.setId(99L);
        otherUser.setEmail("other@example.com");
        otherUser.setRole(Role.USER);

        assertFalse(jwtService.isTokenValid(token, otherUser));
    }

    @Test
    void isTokenValid_returnsFalseWhenTokenVersionChanges() {
        String token = jwtService.generateToken(user);

        user.setTokenVersion(1);

        assertFalse(jwtService.isTokenValid(token, user));
    }

    @Test
    void isTokenValid_returnsFalseWhenExpired() {
        ReflectionTestUtils.setField(jwtService, "expiration", -1L);

        String token = jwtService.generateToken(user);

        assertThrows(io.jsonwebtoken.JwtException.class, () -> jwtService.isTokenValid(token, user));
    }

    @Test
    void validateConfiguration_rejectsShortSecret() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "short");
        ReflectionTestUtils.setField(service, "expiration", 1000L);

        assertThrows(IllegalStateException.class, service::validateConfiguration);
    }

    @Test
    void validateConfiguration_rejectsPlaceholderSecret() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "<enter-your-jwt-configuration-secret-key-here>");
        ReflectionTestUtils.setField(service, "expiration", 1000L);

        assertThrows(IllegalStateException.class, service::validateConfiguration);
    }

    @Test
    void isTokenValid_missingTvClaim_treatsAsZero() {
        String secret = "test-secret-key-that-is-long-enough-for-hmac-sha256";
        String token = Jwts.builder()
                .subject("42")
                .claim("email", "john@example.com")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void extractUserId_rejectsUnsignedNoneAlgToken() {
        String noneToken = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiI0MiIsImVtYWlsIjoiam9obkBleGFtcGxlLmNvbSJ9.";

        assertThrows(io.jsonwebtoken.JwtException.class, () -> jwtService.extractUserId(noneToken));
    }
}

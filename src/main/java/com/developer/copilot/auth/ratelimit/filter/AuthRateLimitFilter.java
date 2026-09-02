package com.developer.copilot.auth.ratelimit.filter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.developer.copilot.auth.config.AuthProperties;
import com.developer.copilot.auth.ratelimit.model.RateLimitResult;
import com.developer.copilot.auth.ratelimit.service.AuthRateLimitService;
import com.developer.copilot.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-IP limit on the public auth URLs that are expensive (BCrypt / SMTP).
 * Per-email limits are applied in {@code AuthServiceImpl} so the body is not consumed here.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-otp",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/refresh-token");

    private static final long WINDOW_SECONDS = 60L;

    private final AuthProperties authProperties;
    private final AuthRateLimitService authRateLimitService;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(AuthProperties authProperties, AuthRateLimitService authRateLimitService) {
        this.authProperties = authProperties;
        this.authRateLimitService = authRateLimitService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !LIMITED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = limitFor(path);
        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitResult result = authRateLimitService.consume(bucketFor(path), clientIp(request), limit, WINDOW_SECONDS);
        if (!result.allowed()) {
            writeTooManyRequests(response, result.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int limitFor(String path) {
        return switch (path) {
            case "/api/v1/auth/login" -> authProperties.getLoginRateLimitPerMinute();
            case "/api/v1/auth/register" -> authProperties.getRegisterRateLimitPerMinute();
            case "/api/v1/auth/verify-email" -> authProperties.getVerifyRateLimitPerMinute();
            case "/api/v1/auth/resend-otp" -> authProperties.getResendRateLimitPerMinute();
            case "/api/v1/auth/forgot-password" -> authProperties.getForgotRateLimitPerMinute();
            case "/api/v1/auth/refresh-token" -> authProperties.getRefreshRateLimitPerMinute();
            default -> 0;
        };
    }

    private static String bucketFor(String path) {
        return switch (path) {
            case "/api/v1/auth/login" -> "login-ip";
            case "/api/v1/auth/register" -> "register-ip";
            case "/api/v1/auth/verify-email" -> "verify-ip";
            case "/api/v1/auth/resend-otp" -> "resend-ip";
            case "/api/v1/auth/forgot-password" -> "forgot-ip";
            case "/api/v1/auth/refresh-token" -> "refresh-ip";
            default -> "auth-ip";
        };
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma < 0 ? forwarded.trim() : forwarded.substring(0, comma).trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.<Void>builder()
                        .success(false)
                        .message("Too many requests. Please try again later.")
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}

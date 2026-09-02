package com.developer.copilot.auth.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.developer.copilot.auth.config.AuthProperties;
import com.developer.copilot.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cheap per-IP sliding window on the public auth URLs that are expensive (BCrypt / SMTP).
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-otp",
            "/api/v1/auth/forgot-password");

    private static final long WINDOW_MS = 60_000L;

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(AuthProperties authProperties) {
        this.authProperties = authProperties;
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

        String key = clientIp(request) + ":" + path;
        long now = System.currentTimeMillis();
        Deque<Long> window = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                window.removeFirst();
            }
            if (window.size() >= limit) {
                writeTooManyRequests(response);
                return;
            }
            window.addLast(now);
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
            default -> 0;
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

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.<Void>builder()
                        .success(false)
                        .message("Too many requests. Please try again later.")
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}

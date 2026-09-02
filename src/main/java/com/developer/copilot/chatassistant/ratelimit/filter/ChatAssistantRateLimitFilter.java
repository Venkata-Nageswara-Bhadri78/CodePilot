package com.developer.copilot.chatassistant.ratelimit.filter;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.developer.copilot.auth.security.CustomUserDetails;
import com.developer.copilot.chatassistant.ratelimit.config.ChatAssistantRateLimitProperties;
import com.developer.copilot.chatassistant.ratelimit.model.RateLimitResult;
import com.developer.copilot.chatassistant.ratelimit.service.ChatAssistantRateLimitService;
import com.developer.copilot.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-IP and per-user limits on paid job-chat sends. Runs after the security chain so a
 * stolen JWT is keyed by user id. GET history/list and DELETE are skipped.
 */
public class ChatAssistantRateLimitFilter extends OncePerRequestFilter {

    private static final String PATH_PREFIX = "/api/v1/chat-assistant";
    private static final long WINDOW_SECONDS = 60L;

    private final ChatAssistantRateLimitProperties properties;
    private final ChatAssistantRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public ChatAssistantRateLimitFilter(
            ChatAssistantRateLimitProperties properties,
            ChatAssistantRateLimitService rateLimitService) {
        this.properties = properties;
        this.rateLimitService = rateLimitService;
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
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase();
        String bucket = bucketFor(method, path);
        int limit = limitFor(bucket);
        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitResult ipResult = rateLimitService.consume(
                bucket + "-ip", clientIp(request), limit, WINDOW_SECONDS);
        if (!ipResult.allowed()) {
            writeTooManyRequests(response, ipResult.retryAfterSeconds());
            return;
        }

        String userId = currentUserId();
        if (userId != null) {
            RateLimitResult userResult = rateLimitService.consume(
                    bucket + "-user", userId, limit, WINDOW_SECONDS);
            if (!userResult.allowed()) {
                writeTooManyRequests(response, userResult.retryAfterSeconds());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    static String bucketFor(String method, String path) {
        if (path == null || !isChatAssistantPath(path)) {
            return "other";
        }
        if ("POST".equals(method) && path.contains("/jobs/") && path.endsWith("/messages")) {
            return "messages";
        }
        return "other";
    }

    private int limitFor(String bucket) {
        if ("messages".equals(bucket)) {
            return properties.getMessagesPerMinute();
        }
        return 0;
    }

    private static boolean isChatAssistantPath(String path) {
        return path.equals(PATH_PREFIX) || path.startsWith(PATH_PREFIX + "/");
    }

    private static String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails details
                && details.getUser() != null
                && details.getUser().getId() != null) {
            return String.valueOf(details.getUser().getId());
        }
        return null;
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

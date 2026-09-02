package com.developer.copilot.user.ratelimit.filter;

import com.developer.copilot.auth.security.CustomUserDetails;
import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.user.ratelimit.config.UserRateLimitProperties;
import com.developer.copilot.user.ratelimit.model.RateLimitResult;
import com.developer.copilot.user.ratelimit.service.UserRateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Per-IP and per-user limits on resume upload and internal parse reads.
 * Runs after the security chain so a stolen JWT is keyed by user id.
 */
public class UserRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_SECONDS = 60L;

    private final UserRateLimitProperties properties;
    private final UserRateLimitService userRateLimitService;
    private final ObjectMapper objectMapper;

    public UserRateLimitFilter(
            UserRateLimitProperties properties,
            UserRateLimitService userRateLimitService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.userRateLimitService = userRateLimitService;
        this.objectMapper = objectMapper;
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

        RateLimitResult ipResult = userRateLimitService.consume(
                bucket + "-ip", clientIp(request), limit, WINDOW_SECONDS);
        if (!ipResult.allowed()) {
            writeTooManyRequests(response, ipResult.retryAfterSeconds());
            return;
        }

        String userId = currentUserId();
        if (userId != null) {
            RateLimitResult userResult = userRateLimitService.consume(
                    bucket + "-user", userId, limit, WINDOW_SECONDS);
            if (!userResult.allowed()) {
                writeTooManyRequests(response, userResult.retryAfterSeconds());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    static String bucketFor(String method, String path) {
        if ("POST".equals(method) && isResumeCollection(path)) {
            return "upload";
        }
        if ("GET".equals(method) && isInternalParse(path)) {
            return "parse";
        }
        return "other";
    }

    private int limitFor(String bucket) {
        return switch (bucket) {
            case "upload" -> properties.getUploadPerMinute();
            case "parse" -> properties.getParsePerMinute();
            default -> 0;
        };
    }

    private static boolean isResumeCollection(String path) {
        return "/api/v1/users/resumes".equals(path);
    }

    private static boolean isInternalParse(String path) {
        if (path == null) {
            return false;
        }
        return "/api/v1/internal/resumes/parsed".equals(path)
                || (path.startsWith("/api/v1/internal/resumes/") && path.endsWith("/parsed"));
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
        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .message("Too many requests. Please try again later.")
                .timestamp(LocalDateTime.now())
                .build();

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

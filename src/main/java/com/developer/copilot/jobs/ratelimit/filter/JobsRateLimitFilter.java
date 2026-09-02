package com.developer.copilot.jobs.ratelimit.filter;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.developer.copilot.auth.security.CustomUserDetails;
import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.jobs.ratelimit.config.JobsRateLimitProperties;
import com.developer.copilot.jobs.ratelimit.model.RateLimitResult;
import com.developer.copilot.jobs.ratelimit.service.JobsRateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-IP and per-user limits on {@code /api/v1/jobs/**}. Runs after the security chain
 * so a stolen JWT is keyed by user id, not only by source IP. Same generic 429 for both.
 */
public class JobsRateLimitFilter extends OncePerRequestFilter {

    private static final String JOBS_PREFIX = "/api/v1/jobs";
    private static final long WINDOW_SECONDS = 60L;

    private final JobsRateLimitProperties properties;
    private final JobsRateLimitService jobsRateLimitService;
    private final ObjectMapper objectMapper;

    public JobsRateLimitFilter(
            JobsRateLimitProperties properties,
            JobsRateLimitService jobsRateLimitService) {
        this.properties = properties;
        this.jobsRateLimitService = jobsRateLimitService;
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
        if (!isJobsPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String bucket = bucketFor(request, path);
        int limit = limitFor(bucket);
        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitResult ipResult = jobsRateLimitService.consume(
                bucket + "-ip", clientIp(request), limit, WINDOW_SECONDS);
        if (!ipResult.allowed()) {
            writeTooManyRequests(response, ipResult.retryAfterSeconds());
            return;
        }

        String userId = currentUserId();
        if (userId != null) {
            RateLimitResult userResult = jobsRateLimitService.consume(
                    bucket + "-user", userId, limit, WINDOW_SECONDS);
            if (!userResult.allowed()) {
                writeTooManyRequests(response, userResult.retryAfterSeconds());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private int limitFor(String bucket) {
        return switch (bucket) {
            case "post" -> properties.getPostPerMinute();
            case "mutate" -> properties.getMutatePerMinute();
            case "list" -> properties.getListPerMinute();
            case "search" -> properties.getSearchPerMinute();
            case "read" -> properties.getReadPerMinute();
            default -> 0;
        };
    }

    static String bucketFor(HttpServletRequest request, String path) {
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase();
        return switch (method) {
            case "POST" -> "post";
            case "PUT", "PATCH", "DELETE" -> "mutate";
            case "GET" -> isCollectionPath(path)
                    ? (hasQuery(request) ? "search" : "list")
                    : "read";
            default -> "other";
        };
    }

    private static boolean isJobsPath(String path) {
        return path != null && (path.equals(JOBS_PREFIX) || path.startsWith(JOBS_PREFIX + "/"));
    }

    private static boolean isCollectionPath(String path) {
        return JOBS_PREFIX.equals(path) || (JOBS_PREFIX + "/").equals(path);
    }

    private static boolean hasQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        return query != null && !query.isBlank();
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

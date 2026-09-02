package com.developer.copilot.jobextraction.ratelimit.filter;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.developer.copilot.auth.security.CustomUserDetails;
import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.jobextraction.ratelimit.config.JobExtractionRateLimitProperties;
import com.developer.copilot.jobextraction.ratelimit.model.RateLimitResult;
import com.developer.copilot.jobextraction.ratelimit.service.JobExtractionRateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-IP and per-user limits on {@code /api/v1/job-extraction/**}. Runs after the security
 * chain so a stolen JWT is keyed by user id, not only by source IP. Same generic 429 for both.
 */
public class JobExtractionRateLimitFilter extends OncePerRequestFilter {

    private static final String PATH_PREFIX = "/api/v1/job-extraction";
    private static final long WINDOW_SECONDS = 60L;

    private final JobExtractionRateLimitProperties properties;
    private final JobExtractionRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public JobExtractionRateLimitFilter(
            JobExtractionRateLimitProperties properties,
            JobExtractionRateLimitService rateLimitService) {
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
        if (!isExtractionPath(path) || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = properties.getParsePerMinute();
        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitResult ipResult = rateLimitService.consume(
                "parse-ip", clientIp(request), limit, WINDOW_SECONDS);
        if (!ipResult.allowed()) {
            writeTooManyRequests(response, ipResult.retryAfterSeconds());
            return;
        }

        String userId = currentUserId();
        if (userId != null) {
            RateLimitResult userResult = rateLimitService.consume(
                    "parse-user", userId, limit, WINDOW_SECONDS);
            if (!userResult.allowed()) {
                writeTooManyRequests(response, userResult.retryAfterSeconds());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isExtractionPath(String path) {
        return path != null && (path.equals(PATH_PREFIX) || path.startsWith(PATH_PREFIX + "/"));
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

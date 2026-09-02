package com.developer.copilot.common.ratelimit.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.developer.copilot.auth.security.CustomUserDetails;
import com.developer.copilot.common.config.InternalApiProperties;
import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.common.ratelimit.config.CommonRateLimitProperties;
import com.developer.copilot.common.ratelimit.model.RateLimitResult;
import com.developer.copilot.common.ratelimit.service.CommonRateLimitService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Hallway limit on {@code /api/v1/internal/**}: per calling-service key and per JWT subject.
 * Runs after the security chain so a stolen JWT is keyed by user id. New internal
 * controllers inherit this without copying a user-service filter.
 */
public class InternalApiRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_SECONDS = 60L;
    private static final String KEY_IDENTITY = "service";

    private final InternalApiProperties internalApiProperties;
    private final CommonRateLimitProperties properties;
    private final CommonRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public InternalApiRateLimitFilter(
            InternalApiProperties internalApiProperties,
            CommonRateLimitProperties properties,
            CommonRateLimitService rateLimitService,
            ObjectMapper objectMapper) {
        this.internalApiProperties = internalApiProperties;
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!isInternalPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        int keyLimit = properties.getInternalKeyPerMinute();
        if (keyLimit > 0) {
            RateLimitResult keyResult = rateLimitService.consume(
                    "internal-key", KEY_IDENTITY, keyLimit, WINDOW_SECONDS);
            if (!keyResult.allowed()) {
                writeTooManyRequests(response, keyResult.retryAfterSeconds());
                return;
            }
        }

        String userId = currentUserId();
        int userLimit = properties.getInternalUserPerMinute();
        if (userId != null && userLimit > 0) {
            RateLimitResult userResult = rateLimitService.consume(
                    "internal-user", userId, userLimit, WINDOW_SECONDS);
            if (!userResult.allowed()) {
                writeTooManyRequests(response, userResult.retryAfterSeconds());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isInternalPath(String path) {
        if (path == null) {
            return false;
        }
        String prefix = normalizePrefix(internalApiProperties.getPathPrefix());
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    public static String normalizePrefix(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isBlank()) {
            return "/api/v1/internal";
        }
        String prefix = pathPrefix.trim();
        while (prefix.endsWith("/") && prefix.length() > 1) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
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

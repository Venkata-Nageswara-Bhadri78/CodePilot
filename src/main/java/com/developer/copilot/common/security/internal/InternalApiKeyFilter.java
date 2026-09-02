package com.developer.copilot.common.security.internal;

import com.developer.copilot.common.config.InternalApiProperties;
import com.developer.copilot.common.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;

/**
 * Requires a shared service key on internal service-to-service endpoints.
 * <p>
 * Runs after the Spring Security chain, so the caller's JWT has already been
 * validated by the time this executes. The key identifies the calling service; the
 * JWT identifies the user whose data is being requested.
 * <p>
 * {@code internal.api.enabled=false} skips the key only on {@code local}/{@code dev}
 * profiles (laptop). Anywhere else a disabled flag still rejects the request.
 */
@Slf4j
@RequiredArgsConstructor
public class InternalApiKeyFilter extends OncePerRequestFilter {

    /** Client-facing 401 for every key failure so responses do not distinguish misconfig from a wrong secret. */
    public static final String UNAUTHORIZED_CLIENT_MESSAGE =
            "Invalid or missing internal service key.";

    private final InternalApiProperties internalApiProperties;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!internalApiProperties.isEnabled()) {
            if (isLaptopProfile()) {
                filterChain.doFilter(request, response);
                return;
            }
            log.error("Internal API is disabled outside local/dev; rejecting {} {}",
                    request.getMethod(), request.getRequestURI());
            reject(response);
            return;
        }

        String configuredKey = internalApiProperties.getKey();

        if (configuredKey == null || configuredKey.isBlank()) {
            log.error("Internal API is enabled but '{}' is not configured; rejecting {} {}",
                    "internal.api.key", request.getMethod(), request.getRequestURI());
            reject(response);
            return;
        }

        if (!matches(configuredKey, request.getHeader(internalApiProperties.getHeaderName()))) {
            log.warn("Rejected internal request to {} with missing or invalid service key",
                    request.getRequestURI());
            reject(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLaptopProfile() {
        if (environment == null) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("local") || profile.equals("dev"));
    }

    private boolean matches(String configuredKey, String providedKey) {
        if (providedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response) throws IOException {

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .message(UNAUTHORIZED_CLIENT_MESSAGE)
                .timestamp(LocalDateTime.now())
                .build();

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

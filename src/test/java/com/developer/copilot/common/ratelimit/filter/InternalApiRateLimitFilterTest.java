package com.developer.copilot.common.ratelimit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.security.CustomUserDetails;
import com.developer.copilot.common.config.InternalApiProperties;
import com.developer.copilot.common.ratelimit.config.CommonRateLimitConfig;
import com.developer.copilot.common.ratelimit.config.CommonRateLimitProperties;
import com.developer.copilot.common.ratelimit.service.impl.CommonRateLimitServiceImpl;

import jakarta.servlet.FilterChain;
import tools.jackson.databind.ObjectMapper;

class InternalApiRateLimitFilterTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void internalPath_isLimitedPerServiceKey() throws Exception {
        InternalApiRateLimitFilter filter = newFilter(tightLimits());
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/internal/resumes/parsed");
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest blocked = new MockHttpServletRequest("GET", "/api/v1/internal/other");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilter(blocked, blockedResponse, chain);

        assertEquals(429, blockedResponse.getStatus());
        assertNotNull(blockedResponse.getHeader("Retry-After"));
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void internalPath_isLimitedPerUserAcrossPaths() throws Exception {
        InternalApiRateLimitFilter filter = newFilter(tightUserLimit());
        FilterChain chain = mock(FilterChain.class);
        authenticateUser(7L);

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/v1/internal/resumes/parsed");
        filter.doFilter(first, new MockHttpServletResponse(), chain);

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/v1/internal/foo");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(second, blocked, chain);

        assertEquals(429, blocked.getStatus());
        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nonInternalPath_isNotLimited() throws Exception {
        InternalApiRateLimitFilter filter = newFilter(tightLimits());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/jobs");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void zeroLimits_alwaysAllow() throws Exception {
        CommonRateLimitProperties properties = new CommonRateLimitProperties();
        properties.setInternalKeyPerMinute(0);
        properties.setInternalUserPerMinute(0);
        InternalApiRateLimitFilter filter = newFilter(properties);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/internal/resumes/parsed"),
                new MockHttpServletResponse(),
                chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void filterRegistration_matchesInternalPrefix() {
        InternalApiProperties internal = new InternalApiProperties();
        CommonRateLimitConfig config = new CommonRateLimitConfig();
        InternalApiRateLimitFilter filter = newFilter(new CommonRateLimitProperties());

        var registration = config.internalApiRateLimitFilterRegistration(internal, filter);

        assertEquals(true, registration.getUrlPatterns().contains("/api/v1/internal"));
        assertEquals(true, registration.getUrlPatterns().contains("/api/v1/internal/*"));
    }

    private static InternalApiRateLimitFilter newFilter(CommonRateLimitProperties properties) {
        return new InternalApiRateLimitFilter(
                new InternalApiProperties(),
                properties,
                new CommonRateLimitServiceImpl(null),
                new ObjectMapper());
    }

    private static CommonRateLimitProperties tightLimits() {
        CommonRateLimitProperties properties = new CommonRateLimitProperties();
        properties.setInternalKeyPerMinute(2);
        properties.setInternalUserPerMinute(100);
        return properties;
    }

    private static CommonRateLimitProperties tightUserLimit() {
        CommonRateLimitProperties properties = new CommonRateLimitProperties();
        properties.setInternalKeyPerMinute(100);
        properties.setInternalUserPerMinute(1);
        return properties;
    }

    private static void authenticateUser(long userId) {
        User user = new User();
        user.setId(userId);
        CustomUserDetails details = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }
}

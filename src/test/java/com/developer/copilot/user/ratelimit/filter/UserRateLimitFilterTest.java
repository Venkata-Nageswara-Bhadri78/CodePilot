package com.developer.copilot.user.ratelimit.filter;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.security.CustomUserDetails;
import com.developer.copilot.user.ratelimit.config.UserRateLimitConfig;
import com.developer.copilot.user.ratelimit.config.UserRateLimitProperties;
import com.developer.copilot.user.ratelimit.service.impl.UserRateLimitServiceImpl;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class UserRateLimitFilterTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void upload_isLimitedPerIp() throws Exception {
        UserRateLimitFilter filter = newFilter(tightUploadLimit());
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users/resumes");
            request.setRemoteAddr("10.0.0.1");
            filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest blocked = new MockHttpServletRequest("POST", "/api/v1/users/resumes");
        blocked.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(blocked, blockedResponse, chain);

        assertEquals(429, blockedResponse.getStatus());
        assertNotNull(blockedResponse.getHeader("Retry-After"));
        assertTrue(blockedResponse.getContentAsString().contains("Too many requests"));
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void upload_isLimitedPerUser_acrossIps() throws Exception {
        UserRateLimitFilter filter = newFilter(tightUploadLimit());
        FilterChain chain = mock(FilterChain.class);
        authenticateUser(7L);

        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/api/v1/users/resumes");
        first.setRemoteAddr("10.0.0.1");
        filter.doFilterInternal(first, new MockHttpServletResponse(), chain);

        MockHttpServletRequest second = new MockHttpServletRequest("POST", "/api/v1/users/resumes");
        second.setRemoteAddr("10.0.0.2");
        filter.doFilterInternal(second, new MockHttpServletResponse(), chain);

        MockHttpServletRequest third = new MockHttpServletRequest("POST", "/api/v1/users/resumes");
        third.setRemoteAddr("10.0.0.3");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(third, blocked, chain);

        assertEquals(429, blocked.getStatus());
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void internalParse_isLimited() throws Exception {
        UserRateLimitProperties properties = new UserRateLimitProperties();
        properties.setParsePerMinute(1);
        UserRateLimitFilter filter = newFilter(properties);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest allowed = new MockHttpServletRequest("GET", "/api/v1/internal/resumes/parsed");
        allowed.setRemoteAddr("10.0.0.1");
        filter.doFilterInternal(allowed, new MockHttpServletResponse(), chain);

        MockHttpServletRequest byId = new MockHttpServletRequest("GET", "/api/v1/internal/resumes/5/parsed");
        byId.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(byId, blocked, chain);

        assertEquals(429, blocked.getStatus());
        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listResumes_isNotLimited() throws Exception {
        UserRateLimitFilter filter = newFilter(tightUploadLimit());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/resumes");
        request.setRemoteAddr("10.0.0.1");

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registration_coversUploadAndParsePaths() {
        UserRateLimitConfig config = new UserRateLimitConfig();
        var registration = config.userRateLimitFilterRegistration(
                newFilter(new UserRateLimitProperties()));

        assertTrue(registration.getUrlPatterns().contains("/api/v1/users/resumes"));
        assertTrue(registration.getUrlPatterns().contains("/api/v1/internal/resumes/*"));
    }

    private static UserRateLimitFilter newFilter(UserRateLimitProperties properties) {
        return new UserRateLimitFilter(properties, new UserRateLimitServiceImpl(), new ObjectMapper());
    }

    private static UserRateLimitProperties tightUploadLimit() {
        UserRateLimitProperties properties = new UserRateLimitProperties();
        properties.setUploadPerMinute(2);
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

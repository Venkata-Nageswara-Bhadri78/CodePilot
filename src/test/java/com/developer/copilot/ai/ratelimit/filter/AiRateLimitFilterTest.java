package com.developer.copilot.ai.ratelimit.filter;

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

import com.developer.copilot.ai.ratelimit.config.AiRateLimitProperties;
import com.developer.copilot.ai.ratelimit.service.impl.AiRateLimitServiceImpl;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.security.CustomUserDetails;

import jakarta.servlet.FilterChain;

class AiRateLimitFilterTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void postChat_isLimitedPerIp() throws Exception {
        AiRateLimitFilter filter = newFilter(tightChatLimit());
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ai/chat");
            request.setRemoteAddr("10.0.0.1");
            filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest blocked = new MockHttpServletRequest("POST", "/api/v1/ai/chat");
        blocked.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(blocked, blockedResponse, chain);

        assertEquals(429, blockedResponse.getStatus());
        assertNotNull(blockedResponse.getHeader("Retry-After"));
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void postChat_isLimitedPerUserAcrossIps() throws Exception {
        AiRateLimitFilter filter = newFilter(tightChatLimit());
        FilterChain chain = mock(FilterChain.class);
        authenticateUser(7L);

        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/api/v1/ai/chat");
        first.setRemoteAddr("10.0.0.1");
        filter.doFilterInternal(first, new MockHttpServletResponse(), chain);

        MockHttpServletRequest second = new MockHttpServletRequest("POST", "/api/v1/ai/chat/stream");
        second.setRemoteAddr("10.0.0.2");
        filter.doFilterInternal(second, new MockHttpServletResponse(), chain);

        MockHttpServletRequest third = new MockHttpServletRequest("POST", "/api/v1/ai/chat");
        third.setRemoteAddr("10.0.0.3");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(third, blocked, chain);

        assertEquals(429, blocked.getStatus());
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resumeContext_isLimitedSeparatelyFromChat() throws Exception {
        AiRateLimitFilter filter = newFilter(tightChatLimit());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest context = new MockHttpServletRequest("GET", "/api/v1/ai/resume-context");
        context.setRemoteAddr("10.0.0.1");
        filter.doFilterInternal(context, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void healthAndConfig_areNotLimited() throws Exception {
        AiRateLimitFilter filter = newFilter(tightChatLimit());
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(
                new MockHttpServletRequest("GET", "/api/v1/ai/health"),
                new MockHttpServletResponse(),
                chain);
        filter.doFilterInternal(
                new MockHttpServletRequest("GET", "/api/v1/ai/config"),
                new MockHttpServletResponse(),
                chain);

        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void otherPaths_areNotLimited() throws Exception {
        AiRateLimitFilter filter = newFilter(tightChatLimit());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/jobs");

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static AiRateLimitFilter newFilter(AiRateLimitProperties properties) {
        return new AiRateLimitFilter(properties, new AiRateLimitServiceImpl(null));
    }

    private static AiRateLimitProperties tightChatLimit() {
        AiRateLimitProperties properties = new AiRateLimitProperties();
        properties.setChatPerMinute(2);
        properties.setResumeContextPerMinute(20);
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

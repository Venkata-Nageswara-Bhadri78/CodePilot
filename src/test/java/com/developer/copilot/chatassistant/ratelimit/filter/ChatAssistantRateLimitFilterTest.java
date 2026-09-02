package com.developer.copilot.chatassistant.ratelimit.filter;

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
import com.developer.copilot.chatassistant.ratelimit.config.ChatAssistantRateLimitProperties;
import com.developer.copilot.chatassistant.ratelimit.service.impl.ChatAssistantRateLimitServiceImpl;

import jakarta.servlet.FilterChain;

class ChatAssistantRateLimitFilterTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void postMessages_isLimitedPerIp() throws Exception {
        ChatAssistantRateLimitFilter filter = newFilter(tightLimit());
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = postMessages("10.0.0.1");
            filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(postMessages("10.0.0.1"), blockedResponse, chain);

        assertEquals(429, blockedResponse.getStatus());
        assertNotNull(blockedResponse.getHeader("Retry-After"));
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void postMessages_isLimitedPerUserAcrossIps() throws Exception {
        ChatAssistantRateLimitFilter filter = newFilter(tightLimit());
        FilterChain chain = mock(FilterChain.class);
        authenticateUser(7L);

        filter.doFilterInternal(postMessages("10.0.0.1"), new MockHttpServletResponse(), chain);
        filter.doFilterInternal(postMessages("10.0.0.2"), new MockHttpServletResponse(), chain);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(postMessages("10.0.0.3"), blocked, chain);

        assertEquals(429, blocked.getStatus());
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getHistoryAndList_areNotLimited() throws Exception {
        ChatAssistantRateLimitFilter filter = newFilter(tightLimit());
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(
                new MockHttpServletRequest("GET", "/api/v1/chat-assistant/jobs/1"),
                new MockHttpServletResponse(),
                chain);
        filter.doFilterInternal(
                new MockHttpServletRequest("GET", "/api/v1/chat-assistant"),
                new MockHttpServletResponse(),
                chain);
        filter.doFilterInternal(
                new MockHttpServletRequest("DELETE", "/api/v1/chat-assistant/jobs/1"),
                new MockHttpServletResponse(),
                chain);

        verify(chain, times(3)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void otherPaths_areNotLimited() throws Exception {
        ChatAssistantRateLimitFilter filter = newFilter(tightLimit());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ai/chat");

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bucketFor_messagesPath() {
        assertEquals("messages", ChatAssistantRateLimitFilter.bucketFor(
                "POST", "/api/v1/chat-assistant/jobs/42/messages"));
        assertEquals("other", ChatAssistantRateLimitFilter.bucketFor(
                "GET", "/api/v1/chat-assistant/jobs/42"));
    }

    private static MockHttpServletRequest postMessages(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/api/v1/chat-assistant/jobs/1/messages");
        request.setRemoteAddr(ip);
        return request;
    }

    private static ChatAssistantRateLimitFilter newFilter(ChatAssistantRateLimitProperties properties) {
        return new ChatAssistantRateLimitFilter(properties, new ChatAssistantRateLimitServiceImpl(null));
    }

    private static ChatAssistantRateLimitProperties tightLimit() {
        ChatAssistantRateLimitProperties properties = new ChatAssistantRateLimitProperties();
        properties.setMessagesPerMinute(2);
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

package com.developer.copilot.auth.security;

import com.developer.copilot.auth.config.AuthProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AuthRateLimitFilterTest {

    @Test
    void login_isLimitedPerIp() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setLoginRateLimitPerMinute(2);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(properties);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, chain);
            assertEquals(200, response.getStatus() == 0 ? 200 : response.getStatus());
        }

        MockHttpServletRequest blocked = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        blocked.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(blocked, blockedResponse, chain);

        assertEquals(429, blockedResponse.getStatus());
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void otherPaths_areNotLimited() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(new AuthProperties());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}

package com.developer.copilot.jobs.ratelimit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.developer.copilot.jobs.ratelimit.config.JobsRateLimitProperties;
import com.developer.copilot.jobs.ratelimit.service.impl.JobsRateLimitServiceImpl;

import jakarta.servlet.FilterChain;

class JobsRateLimitFilterTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void post_isLimitedPerIp() throws Exception {
        JobsRateLimitProperties properties = tightPostLimit();
        JobsRateLimitFilter filter = newFilter(properties);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/jobs");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, chain);
            assertEquals(200, response.getStatus() == 0 ? 200 : response.getStatus());
        }

        MockHttpServletRequest blocked = new MockHttpServletRequest("POST", "/api/v1/jobs");
        blocked.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(blocked, blockedResponse, chain);

        assertEquals(429, blockedResponse.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(blockedResponse.getHeader("Retry-After"));
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void post_isLimitedPerUser_acrossIps() throws Exception {
        JobsRateLimitProperties properties = tightPostLimit();
        JobsRateLimitFilter filter = newFilter(properties);
        FilterChain chain = mock(FilterChain.class);
        authenticateUser(7L);

        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/api/v1/jobs");
        first.setRemoteAddr("10.0.0.1");
        filter.doFilterInternal(first, new MockHttpServletResponse(), chain);

        MockHttpServletRequest second = new MockHttpServletRequest("POST", "/api/v1/jobs");
        second.setRemoteAddr("10.0.0.2");
        filter.doFilterInternal(second, new MockHttpServletResponse(), chain);

        MockHttpServletRequest third = new MockHttpServletRequest("POST", "/api/v1/jobs");
        third.setRemoteAddr("10.0.0.3");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(third, blocked, chain);

        assertEquals(429, blocked.getStatus());
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listWithQuery_usesStricterSearchBucket() throws Exception {
        JobsRateLimitProperties properties = new JobsRateLimitProperties();
        properties.setSearchPerMinute(1);
        properties.setListPerMinute(50);
        JobsRateLimitFilter filter = newFilter(properties);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest allowed = new MockHttpServletRequest("GET", "/api/v1/jobs");
        allowed.setQueryString("search=java");
        allowed.setRemoteAddr("10.0.0.1");
        filter.doFilterInternal(allowed, new MockHttpServletResponse(), chain);

        MockHttpServletRequest blocked = new MockHttpServletRequest("GET", "/api/v1/jobs");
        blocked.setQueryString("search=java");
        blocked.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(blocked, blockedResponse, chain);

        assertEquals(429, blockedResponse.getStatus());
        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void patch_usesMutateBucket() throws Exception {
        JobsRateLimitProperties properties = new JobsRateLimitProperties();
        properties.setMutatePerMinute(1);
        JobsRateLimitFilter filter = newFilter(properties);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest first = new MockHttpServletRequest("PATCH", "/api/v1/jobs/42");
        first.setRemoteAddr("10.0.0.1");
        filter.doFilterInternal(first, new MockHttpServletResponse(), chain);

        MockHttpServletRequest second = new MockHttpServletRequest("PUT", "/api/v1/jobs/42");
        second.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(second, blocked, chain);

        assertEquals(429, blocked.getStatus());
    }

    @Test
    void otherPaths_areNotLimited() throws Exception {
        JobsRateLimitFilter filter = newFilter(tightPostLimit());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus() == 0 ? 200 : response.getStatus());
    }

    private static JobsRateLimitFilter newFilter(JobsRateLimitProperties properties) {
        return new JobsRateLimitFilter(properties, new JobsRateLimitServiceImpl(null));
    }

    private static JobsRateLimitProperties tightPostLimit() {
        JobsRateLimitProperties properties = new JobsRateLimitProperties();
        properties.setPostPerMinute(2);
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

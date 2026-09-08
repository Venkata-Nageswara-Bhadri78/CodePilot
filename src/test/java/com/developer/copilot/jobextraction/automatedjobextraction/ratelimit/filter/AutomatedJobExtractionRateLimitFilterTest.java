package com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.filter;

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
import com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.config.AutomatedJobExtractionRateLimitProperties;
import com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.service.impl.AutomatedJobExtractionRateLimitServiceImpl;

import jakarta.servlet.FilterChain;

class AutomatedJobExtractionRateLimitFilterTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void post_isLimitedPerIp() throws Exception {
        AutomatedJobExtractionRateLimitFilter filter = newFilter(tightLimit());
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("POST", "/api/v1/automated-job-extraction/parse");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, chain);
            assertEquals(200, response.getStatus() == 0 ? 200 : response.getStatus());
        }

        MockHttpServletRequest blocked =
                new MockHttpServletRequest("POST", "/api/v1/automated-job-extraction/parse");
        blocked.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(blocked, blockedResponse, chain);

        assertEquals(429, blockedResponse.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(blockedResponse.getHeader("Retry-After"));
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void post_isLimitedPerUser_acrossIps() throws Exception {
        AutomatedJobExtractionRateLimitFilter filter = newFilter(tightLimit());
        FilterChain chain = mock(FilterChain.class);
        authenticateUser(7L);

        MockHttpServletRequest first =
                new MockHttpServletRequest("POST", "/api/v1/automated-job-extraction/parse");
        first.setRemoteAddr("10.0.0.1");
        filter.doFilterInternal(first, new MockHttpServletResponse(), chain);

        MockHttpServletRequest second =
                new MockHttpServletRequest("POST", "/api/v1/automated-job-extraction/parse");
        second.setRemoteAddr("10.0.0.2");
        filter.doFilterInternal(second, new MockHttpServletResponse(), chain);

        MockHttpServletRequest third =
                new MockHttpServletRequest("POST", "/api/v1/automated-job-extraction/parse");
        third.setRemoteAddr("10.0.0.3");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(third, blocked, chain);

        assertEquals(429, blocked.getStatus());
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void otherPaths_areNotLimited() throws Exception {
        AutomatedJobExtractionRateLimitFilter filter = newFilter(tightLimit());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/job-extraction/parse");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus() == 0 ? 200 : response.getStatus());
    }

    @Test
    void get_isNotLimited() throws Exception {
        AutomatedJobExtractionRateLimitFilter filter = newFilter(tightLimit());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/automated-job-extraction/parse");
        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static AutomatedJobExtractionRateLimitFilter newFilter(
            AutomatedJobExtractionRateLimitProperties properties) {
        return new AutomatedJobExtractionRateLimitFilter(
                properties, new AutomatedJobExtractionRateLimitServiceImpl(null));
    }

    private static AutomatedJobExtractionRateLimitProperties tightLimit() {
        AutomatedJobExtractionRateLimitProperties properties = new AutomatedJobExtractionRateLimitProperties();
        properties.setParsePerMinute(2);
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

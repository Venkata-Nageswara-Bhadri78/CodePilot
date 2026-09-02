package com.developer.copilot.common.security.internal;

import com.developer.copilot.common.config.InternalApiProperties;
import com.developer.copilot.common.config.InternalApiSecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalApiKeyFilterTest {

    private InternalApiProperties properties;
    private MockEnvironment environment;
    private InternalApiKeyFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        properties = new InternalApiProperties();
        properties.setEnabled(true);
        properties.setKey("super-secret-service-key");
        environment = new MockEnvironment();

        filter = new InternalApiKeyFilter(properties, new ObjectMapper(), environment);

        request = new MockHttpServletRequest("GET", "/api/v1/internal/resumes/parsed");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @Test
    void validKey_passesThrough() throws Exception {
        request.addHeader("X-Internal-Api-Key", "super-secret-service-key");

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    @Test
    void missingKey_isRejected() throws Exception {
        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));
        assertTrue(response.getContentAsString().contains("Invalid or missing internal service key."));
    }

    @Test
    void invalidKey_isRejected() throws Exception {
        request.addHeader("X-Internal-Api-Key", "wrong-key");

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
    }

    @Test
    void unconfiguredKey_failsClosed() throws Exception {
        properties.setKey("   ");
        request.addHeader("X-Internal-Api-Key", "anything");

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        assertTrue(response.getContentAsString().contains("Internal API is not configured."));
    }

    @Test
    void disabledInternalApi_onLaptopProfile_skipsKeyCheck() throws Exception {
        properties.setEnabled(false);
        environment.setActiveProfiles("local");

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    @Test
    void disabledInternalApi_onDevProfile_skipsKeyCheck() throws Exception {
        properties.setEnabled(false);
        environment.setActiveProfiles("dev");

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    @Test
    void disabledInternalApi_outsideLaptop_failsClosed() throws Exception {
        properties.setEnabled(false);

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        assertTrue(response.getContentAsString().contains("Internal API is not configured."));
    }

    @Test
    void customHeaderName_isHonoured() throws Exception {
        properties.setHeaderName("X-Service-Key");
        request.addHeader("X-Service-Key", "super-secret-service-key");

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void comparisonIsSafeForKeysOfDifferentLengths() throws Exception {
        request.addHeader("X-Internal-Api-Key", "super-secret-service-key-but-longer");

        filter.doFilter(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
    }

    @Test
    void differingOnlyInLastCharacterOrFirstCharacter_areBothRejectedIdentically() throws Exception {
        MockHttpServletRequest diffAtEnd = new MockHttpServletRequest("GET", "/api/v1/internal/resumes/parsed");
        diffAtEnd.addHeader("X-Internal-Api-Key", "super-secret-service-kex");
        MockHttpServletResponse diffAtEndResponse = new MockHttpServletResponse();
        filter.doFilter(diffAtEnd, diffAtEndResponse, new MockFilterChain());

        MockHttpServletRequest diffAtStart = new MockHttpServletRequest("GET", "/api/v1/internal/resumes/parsed");
        diffAtStart.addHeader("X-Internal-Api-Key", "xuper-secret-service-key");
        MockHttpServletResponse diffAtStartResponse = new MockHttpServletResponse();
        filter.doFilter(diffAtStart, diffAtStartResponse, new MockFilterChain());

        assertEquals(HttpStatus.UNAUTHORIZED.value(), diffAtEndResponse.getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), diffAtStartResponse.getStatus());
    }

    @Test
    void filterRegistration_isScopedOnlyToInternalPathPrefix() {
        InternalApiSecurityConfig config = new InternalApiSecurityConfig();

        org.springframework.boot.web.servlet.FilterRegistrationBean<InternalApiKeyFilter> registration =
                config.internalApiKeyFilterRegistration(properties, new ObjectMapper(), environment);

        assertTrue(registration.getUrlPatterns().contains("/api/v1/internal/*"));
        assertEquals(1, registration.getUrlPatterns().size());
    }
}

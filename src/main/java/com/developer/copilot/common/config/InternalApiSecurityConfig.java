package com.developer.copilot.common.config;

import com.developer.copilot.common.security.internal.InternalApiKeyFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires up the second, independent authentication layer for {@code /api/v1/internal/**}
 * endpoints (see {@link InternalApiKeyFilter}).
 * <p>
 * Note for anyone reading the main Spring Security {@code SecurityFilterChain} (in the
 * {@code auth} service): that chain only validates the JWT. Requests under the internal
 * path prefix additionally require the shared secret enforced by the filter registered
 * below, which runs afterwards and is intentionally outside Spring Security's own chain.
 */
@Configuration
@EnableConfigurationProperties(InternalApiProperties.class)
public class InternalApiSecurityConfig {

    /**
     * Registered at the lowest precedence so it runs after the Spring Security
     * filter chain, and scoped to the internal path prefix so no public endpoint
     * ever requires the service key.
     */
    @Bean
    public FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilterRegistration(
            InternalApiProperties internalApiProperties,
            ObjectMapper objectMapper) {

        FilterRegistrationBean<InternalApiKeyFilter> registration = new FilterRegistrationBean<>(
                new InternalApiKeyFilter(internalApiProperties, objectMapper));

        registration.setName("internalApiKeyFilter");
        registration.addUrlPatterns(internalApiProperties.getPathPrefix() + "/*");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);

        return registration;
    }
}

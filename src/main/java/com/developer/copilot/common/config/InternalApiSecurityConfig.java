package com.developer.copilot.common.config;

import com.developer.copilot.common.security.internal.InternalApiKeyFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

@Configuration
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

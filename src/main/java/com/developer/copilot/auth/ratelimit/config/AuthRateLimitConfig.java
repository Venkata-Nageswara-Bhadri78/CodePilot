package com.developer.copilot.auth.ratelimit.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.developer.copilot.auth.config.AuthProperties;
import com.developer.copilot.auth.config.ExtensionProperties;
import com.developer.copilot.auth.ratelimit.filter.AuthRateLimitFilter;
import com.developer.copilot.auth.ratelimit.service.AuthRateLimitService;
import com.developer.copilot.auth.ratelimit.service.impl.AuthRateLimitServiceImpl;
import com.developer.copilot.auth.redis.service.AuthRedisService;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, ExtensionProperties.class})
public class AuthRateLimitConfig {

    @Bean
    public AuthRateLimitService authRateLimitService(
            AuthProperties authProperties,
            ObjectProvider<AuthRedisService> redisService) {
        return new AuthRateLimitServiceImpl(authProperties, redisService.getIfAvailable());
    }

    @Bean
    public AuthRateLimitFilter authRateLimitFilter(
            AuthProperties authProperties,
            AuthRateLimitService authRateLimitService,
            ExtensionProperties extensionProperties) {
        return new AuthRateLimitFilter(authProperties, authRateLimitService, extensionProperties);
    }

    /**
     * Keep the filter on the security chain only. A servlet registration would count every
     * request twice.
     */
    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(
            AuthRateLimitFilter authRateLimitFilter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(authRateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }
}

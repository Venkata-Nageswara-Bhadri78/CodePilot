package com.developer.copilot.common.ratelimit.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.developer.copilot.common.config.InternalApiProperties;
import com.developer.copilot.common.ratelimit.filter.InternalApiRateLimitFilter;
import com.developer.copilot.common.ratelimit.service.CommonRateLimitService;
import com.developer.copilot.common.ratelimit.service.impl.CommonRateLimitServiceImpl;
import com.developer.copilot.common.redis.service.CommonRedisService;

import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(CommonRateLimitProperties.class)
public class CommonRateLimitConfig {

    /**
     * After {@code springSecurityFilterChain} (-100) and after the internal API key filter
     * (-90) so 401s are not counted. Same URL patterns as the key filter.
     */
    private static final int AFTER_INTERNAL_KEY = -70;

    @Bean
    public CommonRateLimitService commonRateLimitService(ObjectProvider<CommonRedisService> redisService) {
        return new CommonRateLimitServiceImpl(redisService.getIfAvailable());
    }

    @Bean
    public InternalApiRateLimitFilter internalApiRateLimitFilter(
            InternalApiProperties internalApiProperties,
            CommonRateLimitProperties properties,
            CommonRateLimitService commonRateLimitService,
            ObjectMapper objectMapper) {
        return new InternalApiRateLimitFilter(
                internalApiProperties, properties, commonRateLimitService, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<InternalApiRateLimitFilter> internalApiRateLimitFilterRegistration(
            InternalApiProperties internalApiProperties,
            InternalApiRateLimitFilter internalApiRateLimitFilter) {
        FilterRegistrationBean<InternalApiRateLimitFilter> registration =
                new FilterRegistrationBean<>(internalApiRateLimitFilter);
        registration.setName("internalApiRateLimitFilter");
        String prefix = InternalApiRateLimitFilter.normalizePrefix(internalApiProperties.getPathPrefix());
        registration.addUrlPatterns(prefix, prefix + "/*");
        registration.setOrder(AFTER_INTERNAL_KEY);
        registration.setEnabled(true);
        return registration;
    }
}

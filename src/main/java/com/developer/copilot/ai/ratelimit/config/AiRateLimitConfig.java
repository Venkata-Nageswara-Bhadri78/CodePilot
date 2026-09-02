package com.developer.copilot.ai.ratelimit.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.developer.copilot.ai.ratelimit.filter.AiRateLimitFilter;
import com.developer.copilot.ai.ratelimit.service.AiRateLimitService;
import com.developer.copilot.ai.ratelimit.service.impl.AiRateLimitServiceImpl;
import com.developer.copilot.ai.redis.service.AiRedisService;

@Configuration
@EnableConfigurationProperties(AiRateLimitProperties.class)
public class AiRateLimitConfig {

    /**
     * After {@code springSecurityFilterChain} (default order -100) so JWT has already
     * populated the principal. Not added to the security chain (avoids double-counting).
     */
    private static final int AFTER_SPRING_SECURITY = -80;

    @Bean
    public AiRateLimitService aiRateLimitService(ObjectProvider<AiRedisService> redisService) {
        return new AiRateLimitServiceImpl(redisService.getIfAvailable());
    }

    @Bean
    public AiRateLimitFilter aiRateLimitFilter(
            AiRateLimitProperties properties,
            AiRateLimitService aiRateLimitService) {
        return new AiRateLimitFilter(properties, aiRateLimitService);
    }

    @Bean
    public FilterRegistrationBean<AiRateLimitFilter> aiRateLimitFilterRegistration(
            AiRateLimitFilter aiRateLimitFilter) {
        FilterRegistrationBean<AiRateLimitFilter> registration =
                new FilterRegistrationBean<>(aiRateLimitFilter);
        registration.addUrlPatterns("/api/v1/ai", "/api/v1/ai/*", "/api/v1/ai/chat/*");
        registration.setOrder(AFTER_SPRING_SECURITY);
        registration.setEnabled(true);
        return registration;
    }
}

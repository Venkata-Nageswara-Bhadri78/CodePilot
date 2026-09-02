package com.developer.copilot.user.ratelimit.config;

import com.developer.copilot.user.ratelimit.filter.UserRateLimitFilter;
import com.developer.copilot.user.ratelimit.service.UserRateLimitService;
import com.developer.copilot.user.ratelimit.service.impl.UserRateLimitServiceImpl;
import com.developer.copilot.user.redis.service.UserRedisService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(UserRateLimitProperties.class)
public class UserRateLimitConfig {

    /**
     * After {@code springSecurityFilterChain} (default order -100) so JWT has already
     * populated the principal.
     */
    private static final int AFTER_SPRING_SECURITY = -80;

    @Bean
    public UserRateLimitService userRateLimitService(ObjectProvider<UserRedisService> redisService) {
        return new UserRateLimitServiceImpl(redisService.getIfAvailable());
    }

    @Bean
    public UserRateLimitFilter userRateLimitFilter(
            UserRateLimitProperties properties,
            UserRateLimitService userRateLimitService,
            ObjectMapper objectMapper) {
        return new UserRateLimitFilter(properties, userRateLimitService, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<UserRateLimitFilter> userRateLimitFilterRegistration(
            UserRateLimitFilter userRateLimitFilter) {
        FilterRegistrationBean<UserRateLimitFilter> registration = new FilterRegistrationBean<>(userRateLimitFilter);
        registration.addUrlPatterns(
                "/api/v1/users/resumes",
                "/api/v1/users/resumes/*",
                "/api/v1/internal/resumes",
                "/api/v1/internal/resumes/*");
        registration.setOrder(AFTER_SPRING_SECURITY);
        registration.setEnabled(true);
        return registration;
    }
}

package com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.filter.AutomatedJobExtractionRateLimitFilter;
import com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.service.AutomatedJobExtractionRateLimitService;
import com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.service.impl.AutomatedJobExtractionRateLimitServiceImpl;
import com.developer.copilot.jobextraction.automatedjobextraction.redis.service.AutomatedJobExtractionRedisService;

@Configuration
@EnableConfigurationProperties(AutomatedJobExtractionRateLimitProperties.class)
public class AutomatedJobExtractionRateLimitConfig {

    private static final int AFTER_SPRING_SECURITY = -80;

    @Bean
    public AutomatedJobExtractionRateLimitService automatedJobExtractionRateLimitService(
            ObjectProvider<AutomatedJobExtractionRedisService> redisService) {
        return new AutomatedJobExtractionRateLimitServiceImpl(redisService.getIfAvailable());
    }

    @Bean
    public AutomatedJobExtractionRateLimitFilter automatedJobExtractionRateLimitFilter(
            AutomatedJobExtractionRateLimitProperties properties,
            AutomatedJobExtractionRateLimitService automatedJobExtractionRateLimitService) {
        return new AutomatedJobExtractionRateLimitFilter(properties, automatedJobExtractionRateLimitService);
    }

    @Bean
    public FilterRegistrationBean<AutomatedJobExtractionRateLimitFilter>
            automatedJobExtractionRateLimitFilterRegistration(
                    AutomatedJobExtractionRateLimitFilter automatedJobExtractionRateLimitFilter) {
        FilterRegistrationBean<AutomatedJobExtractionRateLimitFilter> registration =
                new FilterRegistrationBean<>(automatedJobExtractionRateLimitFilter);
        registration.addUrlPatterns(
                "/api/v1/automated-job-extraction", "/api/v1/automated-job-extraction/*");
        registration.setOrder(AFTER_SPRING_SECURITY);
        registration.setEnabled(true);
        return registration;
    }
}

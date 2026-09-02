package com.developer.copilot.jobs.ratelimit.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.developer.copilot.jobs.ratelimit.filter.JobsRateLimitFilter;
import com.developer.copilot.jobs.ratelimit.service.JobsRateLimitService;
import com.developer.copilot.jobs.ratelimit.service.impl.JobsRateLimitServiceImpl;
import com.developer.copilot.jobs.redis.service.JobsRedisService;

@Configuration
@EnableConfigurationProperties(JobsRateLimitProperties.class)
public class JobsRateLimitConfig {

    /**
     * After {@code springSecurityFilterChain} (default order -100) so JWT has already
     * populated the principal. Not added to the security chain (avoids double-counting).
     */
    private static final int AFTER_SPRING_SECURITY = -80;

    @Bean
    public JobsRateLimitService jobsRateLimitService(ObjectProvider<JobsRedisService> redisService) {
        return new JobsRateLimitServiceImpl(redisService.getIfAvailable());
    }

    @Bean
    public JobsRateLimitFilter jobsRateLimitFilter(
            JobsRateLimitProperties properties,
            JobsRateLimitService jobsRateLimitService) {
        return new JobsRateLimitFilter(properties, jobsRateLimitService);
    }

    @Bean
    public FilterRegistrationBean<JobsRateLimitFilter> jobsRateLimitFilterRegistration(
            JobsRateLimitFilter jobsRateLimitFilter) {
        FilterRegistrationBean<JobsRateLimitFilter> registration = new FilterRegistrationBean<>(jobsRateLimitFilter);
        registration.addUrlPatterns("/api/v1/jobs", "/api/v1/jobs/*");
        registration.setOrder(AFTER_SPRING_SECURITY);
        registration.setEnabled(true);
        return registration;
    }
}

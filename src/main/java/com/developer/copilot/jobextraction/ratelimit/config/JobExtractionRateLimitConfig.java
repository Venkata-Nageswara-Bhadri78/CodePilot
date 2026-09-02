package com.developer.copilot.jobextraction.ratelimit.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.developer.copilot.jobextraction.ratelimit.filter.JobExtractionRateLimitFilter;
import com.developer.copilot.jobextraction.ratelimit.service.JobExtractionRateLimitService;
import com.developer.copilot.jobextraction.ratelimit.service.impl.JobExtractionRateLimitServiceImpl;

@Configuration
@EnableConfigurationProperties(JobExtractionRateLimitProperties.class)
public class JobExtractionRateLimitConfig {

    /**
     * After {@code springSecurityFilterChain} (default order -100) so JWT has already
     * populated the principal. Not added to the security chain (avoids double-counting).
     */
    private static final int AFTER_SPRING_SECURITY = -80;

    @Bean
    public JobExtractionRateLimitService jobExtractionRateLimitService() {
        return new JobExtractionRateLimitServiceImpl();
    }

    @Bean
    public JobExtractionRateLimitFilter jobExtractionRateLimitFilter(
            JobExtractionRateLimitProperties properties,
            JobExtractionRateLimitService jobExtractionRateLimitService) {
        return new JobExtractionRateLimitFilter(properties, jobExtractionRateLimitService);
    }

    @Bean
    public FilterRegistrationBean<JobExtractionRateLimitFilter> jobExtractionRateLimitFilterRegistration(
            JobExtractionRateLimitFilter jobExtractionRateLimitFilter) {
        FilterRegistrationBean<JobExtractionRateLimitFilter> registration =
                new FilterRegistrationBean<>(jobExtractionRateLimitFilter);
        registration.addUrlPatterns("/api/v1/job-extraction", "/api/v1/job-extraction/*");
        registration.setOrder(AFTER_SPRING_SECURITY);
        registration.setEnabled(true);
        return registration;
    }
}

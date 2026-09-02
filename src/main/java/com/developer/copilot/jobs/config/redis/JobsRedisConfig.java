package com.developer.copilot.jobs.config.redis;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.developer.copilot.jobs.redis.key.JobsRedisKeyBuilder;
import com.developer.copilot.jobs.redis.repository.JobsRedisRepository;
import com.developer.copilot.jobs.redis.service.JobsRedisService;
import com.developer.copilot.jobs.redis.service.impl.JobsRedisServiceImpl;

/**
 * Jobs-owned Redis beans. Created only when {@code app.jobs.redis.enabled=true}.
 * Boot's Data Redis auto-config stays excluded; localhost:6379 is not required otherwise.
 */
@Configuration
@EnableConfigurationProperties(JobsRedisProperties.class)
public class JobsRedisConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "app.jobs.redis", name = "enabled", havingValue = "true")
    static class Enabled {

        @Bean
        JobsRedisKeyBuilder jobsRedisKeyBuilder(JobsRedisProperties properties) {
            return new JobsRedisKeyBuilder(properties.getKeyPrefix());
        }

        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory jobsRedisConnectionFactory(JobsRedisProperties properties) {
            RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
            standalone.setHostName(properties.getHost());
            standalone.setPort(properties.getPort());
            standalone.setDatabase(properties.getDatabase());
            if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
                standalone.setPassword(properties.getPassword());
            }
            LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .build();
            LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, client);
            factory.setValidateConnection(false);
            return factory;
        }

        @Bean
        StringRedisTemplate jobsStringRedisTemplate(LettuceConnectionFactory jobsRedisConnectionFactory) {
            return new StringRedisTemplate(jobsRedisConnectionFactory);
        }

        @Bean
        JobsRedisRepository jobsRedisRepository(StringRedisTemplate jobsStringRedisTemplate) {
            return new JobsRedisRepository(jobsStringRedisTemplate);
        }

        @Bean
        JobsRedisService jobsRedisService(
                JobsRedisRepository jobsRedisRepository,
                JobsRedisKeyBuilder jobsRedisKeyBuilder) {
            return new JobsRedisServiceImpl(jobsRedisRepository, jobsRedisKeyBuilder);
        }
    }
}

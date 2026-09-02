package com.developer.copilot.jobextraction.config.redis;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.developer.copilot.jobextraction.redis.key.JobExtractionRedisKeyBuilder;
import com.developer.copilot.jobextraction.redis.repository.JobExtractionRedisRepository;
import com.developer.copilot.jobextraction.redis.service.JobExtractionRedisService;
import com.developer.copilot.jobextraction.redis.service.impl.JobExtractionRedisServiceImpl;

/**
 * Job-extraction-owned Redis beans. Created only when {@code app.jobextraction.redis.enabled=true}.
 * Boot's Data Redis auto-config stays excluded; localhost:6379 is not required otherwise.
 */
@Configuration
@EnableConfigurationProperties(JobExtractionRedisProperties.class)
public class JobExtractionRedisConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "app.jobextraction.redis", name = "enabled", havingValue = "true")
    static class Enabled {

        @Bean
        JobExtractionRedisKeyBuilder jobExtractionRedisKeyBuilder(JobExtractionRedisProperties properties) {
            return new JobExtractionRedisKeyBuilder(properties.getKeyPrefix());
        }

        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory jobExtractionRedisConnectionFactory(JobExtractionRedisProperties properties) {
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
        StringRedisTemplate jobExtractionStringRedisTemplate(
                LettuceConnectionFactory jobExtractionRedisConnectionFactory) {
            return new StringRedisTemplate(jobExtractionRedisConnectionFactory);
        }

        @Bean
        JobExtractionRedisRepository jobExtractionRedisRepository(
                StringRedisTemplate jobExtractionStringRedisTemplate) {
            return new JobExtractionRedisRepository(jobExtractionStringRedisTemplate);
        }

        @Bean
        JobExtractionRedisService jobExtractionRedisService(
                JobExtractionRedisRepository jobExtractionRedisRepository,
                JobExtractionRedisKeyBuilder jobExtractionRedisKeyBuilder) {
            return new JobExtractionRedisServiceImpl(jobExtractionRedisRepository, jobExtractionRedisKeyBuilder);
        }
    }
}

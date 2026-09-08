package com.developer.copilot.jobextraction.automatedjobextraction.config.redis;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.developer.copilot.jobextraction.automatedjobextraction.redis.key.AutomatedJobExtractionRedisKeyBuilder;
import com.developer.copilot.jobextraction.automatedjobextraction.redis.repository.AutomatedJobExtractionRedisRepository;
import com.developer.copilot.jobextraction.automatedjobextraction.redis.service.AutomatedJobExtractionRedisService;
import com.developer.copilot.jobextraction.automatedjobextraction.redis.service.impl.AutomatedJobExtractionRedisServiceImpl;

/**
 * Automated-job-extraction Redis beans. Created only when
 * {@code app.automatedjobextraction.redis.enabled=true}.
 */
@Configuration
@EnableConfigurationProperties(AutomatedJobExtractionRedisProperties.class)
public class AutomatedJobExtractionRedisConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "app.automatedjobextraction.redis", name = "enabled", havingValue = "true")
    static class Enabled {

        @Bean
        AutomatedJobExtractionRedisKeyBuilder automatedJobExtractionRedisKeyBuilder(
                AutomatedJobExtractionRedisProperties properties) {
            return new AutomatedJobExtractionRedisKeyBuilder(properties.getKeyPrefix());
        }

        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory automatedJobExtractionRedisConnectionFactory(
                AutomatedJobExtractionRedisProperties properties) {
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
        StringRedisTemplate automatedJobExtractionStringRedisTemplate(
                LettuceConnectionFactory automatedJobExtractionRedisConnectionFactory) {
            return new StringRedisTemplate(automatedJobExtractionRedisConnectionFactory);
        }

        @Bean
        AutomatedJobExtractionRedisRepository automatedJobExtractionRedisRepository(
                StringRedisTemplate automatedJobExtractionStringRedisTemplate) {
            return new AutomatedJobExtractionRedisRepository(automatedJobExtractionStringRedisTemplate);
        }

        @Bean
        AutomatedJobExtractionRedisService automatedJobExtractionRedisService(
                AutomatedJobExtractionRedisRepository automatedJobExtractionRedisRepository,
                AutomatedJobExtractionRedisKeyBuilder automatedJobExtractionRedisKeyBuilder) {
            return new AutomatedJobExtractionRedisServiceImpl(
                    automatedJobExtractionRedisRepository, automatedJobExtractionRedisKeyBuilder);
        }
    }
}

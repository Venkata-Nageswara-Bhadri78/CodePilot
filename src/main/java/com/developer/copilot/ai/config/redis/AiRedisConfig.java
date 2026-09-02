package com.developer.copilot.ai.config.redis;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.developer.copilot.ai.redis.key.AiRedisKeyBuilder;
import com.developer.copilot.ai.redis.repository.AiRedisRepository;
import com.developer.copilot.ai.redis.service.AiRedisService;
import com.developer.copilot.ai.redis.service.impl.AiRedisServiceImpl;

/**
 * AI-owned Redis beans. Created only when {@code app.ai.redis.enabled=true}.
 * Boot's Data Redis auto-config stays excluded; localhost:6379 is not required otherwise.
 */
@Configuration
@EnableConfigurationProperties(AiRedisProperties.class)
public class AiRedisConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "app.ai.redis", name = "enabled", havingValue = "true")
    static class Enabled {

        @Bean
        AiRedisKeyBuilder aiRedisKeyBuilder(AiRedisProperties properties) {
            return new AiRedisKeyBuilder(properties.getKeyPrefix());
        }

        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory aiRedisConnectionFactory(AiRedisProperties properties) {
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
        StringRedisTemplate aiStringRedisTemplate(LettuceConnectionFactory aiRedisConnectionFactory) {
            return new StringRedisTemplate(aiRedisConnectionFactory);
        }

        @Bean
        AiRedisRepository aiRedisRepository(StringRedisTemplate aiStringRedisTemplate) {
            return new AiRedisRepository(aiStringRedisTemplate);
        }

        @Bean
        AiRedisService aiRedisService(
                AiRedisRepository aiRedisRepository,
                AiRedisKeyBuilder aiRedisKeyBuilder) {
            return new AiRedisServiceImpl(aiRedisRepository, aiRedisKeyBuilder);
        }
    }
}

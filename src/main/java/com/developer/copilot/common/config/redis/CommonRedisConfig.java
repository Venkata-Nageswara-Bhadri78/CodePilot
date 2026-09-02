package com.developer.copilot.common.config.redis;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.developer.copilot.common.redis.key.CommonRedisKeyBuilder;
import com.developer.copilot.common.redis.repository.CommonRedisRepository;
import com.developer.copilot.common.redis.service.CommonRedisService;
import com.developer.copilot.common.redis.service.impl.CommonRedisServiceImpl;

/**
 * Common-owned Redis beans for internal API rate-limit counters. Created only when
 * {@code app.common.redis.enabled=true}. Boot's Data Redis auto-config stays excluded;
 * localhost:6379 is not required otherwise.
 */
@Configuration
@EnableConfigurationProperties(CommonRedisProperties.class)
public class CommonRedisConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "app.common.redis", name = "enabled", havingValue = "true")
    static class Enabled {

        @Bean
        CommonRedisKeyBuilder commonRedisKeyBuilder(CommonRedisProperties properties) {
            return new CommonRedisKeyBuilder(properties.getKeyPrefix());
        }

        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory commonRedisConnectionFactory(CommonRedisProperties properties) {
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
        StringRedisTemplate commonStringRedisTemplate(LettuceConnectionFactory commonRedisConnectionFactory) {
            return new StringRedisTemplate(commonRedisConnectionFactory);
        }

        @Bean
        CommonRedisRepository commonRedisRepository(StringRedisTemplate commonStringRedisTemplate) {
            return new CommonRedisRepository(commonStringRedisTemplate);
        }

        @Bean
        CommonRedisService commonRedisService(
                CommonRedisRepository commonRedisRepository,
                CommonRedisKeyBuilder commonRedisKeyBuilder) {
            return new CommonRedisServiceImpl(commonRedisRepository, commonRedisKeyBuilder);
        }
    }
}

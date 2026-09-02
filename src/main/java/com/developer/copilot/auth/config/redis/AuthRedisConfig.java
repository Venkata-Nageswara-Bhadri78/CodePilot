package com.developer.copilot.auth.config.redis;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.developer.copilot.auth.redis.key.AuthRedisKeyBuilder;
import com.developer.copilot.auth.redis.repository.AuthRedisRepository;
import com.developer.copilot.auth.redis.service.AuthRedisService;
import com.developer.copilot.auth.redis.service.impl.AuthRedisServiceImpl;

/**
 * Auth-owned Redis beans. Created only when {@code app.auth.redis.enabled=true}.
 * Boot's Data Redis auto-config is excluded so localhost:6379 is not required otherwise.
 */
@Configuration
@EnableConfigurationProperties(AuthRedisProperties.class)
public class AuthRedisConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "app.auth.redis", name = "enabled", havingValue = "true")
    static class Enabled {

        @Bean
        AuthRedisKeyBuilder authRedisKeyBuilder(AuthRedisProperties properties) {
            return new AuthRedisKeyBuilder(properties.getKeyPrefix());
        }

        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory authRedisConnectionFactory(AuthRedisProperties properties) {
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
        StringRedisTemplate authStringRedisTemplate(LettuceConnectionFactory authRedisConnectionFactory) {
            return new StringRedisTemplate(authRedisConnectionFactory);
        }

        @Bean
        AuthRedisRepository authRedisRepository(StringRedisTemplate authStringRedisTemplate) {
            return new AuthRedisRepository(authStringRedisTemplate);
        }

        @Bean
        AuthRedisService authRedisService(
                AuthRedisRepository authRedisRepository,
                AuthRedisKeyBuilder authRedisKeyBuilder) {
            return new AuthRedisServiceImpl(authRedisRepository, authRedisKeyBuilder);
        }
    }
}

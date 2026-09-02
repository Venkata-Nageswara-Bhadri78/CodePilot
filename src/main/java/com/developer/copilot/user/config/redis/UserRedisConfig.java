package com.developer.copilot.user.config.redis;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.developer.copilot.user.redis.key.UserRedisKeyBuilder;
import com.developer.copilot.user.redis.repository.UserRedisRepository;
import com.developer.copilot.user.redis.service.UserRedisService;
import com.developer.copilot.user.redis.service.impl.UserRedisServiceImpl;

/**
 * User-owned Redis beans. Created only when {@code app.user.redis.enabled=true}.
 * Boot's Data Redis auto-config stays excluded; localhost:6379 is not required otherwise.
 */
@Configuration
@EnableConfigurationProperties(UserRedisProperties.class)
public class UserRedisConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "app.user.redis", name = "enabled", havingValue = "true")
    static class Enabled {

        @Bean
        UserRedisKeyBuilder userRedisKeyBuilder(UserRedisProperties properties) {
            return new UserRedisKeyBuilder(properties.getKeyPrefix());
        }

        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory userRedisConnectionFactory(UserRedisProperties properties) {
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
        StringRedisTemplate userStringRedisTemplate(LettuceConnectionFactory userRedisConnectionFactory) {
            return new StringRedisTemplate(userRedisConnectionFactory);
        }

        @Bean
        UserRedisRepository userRedisRepository(StringRedisTemplate userStringRedisTemplate) {
            return new UserRedisRepository(userStringRedisTemplate);
        }

        @Bean
        UserRedisService userRedisService(
                UserRedisRepository userRedisRepository,
                UserRedisKeyBuilder userRedisKeyBuilder) {
            return new UserRedisServiceImpl(userRedisRepository, userRedisKeyBuilder);
        }
    }
}

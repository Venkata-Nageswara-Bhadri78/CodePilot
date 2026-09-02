package com.developer.copilot.chatassistant.config.redis;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.developer.copilot.chatassistant.redis.key.ChatAssistantRedisKeyBuilder;
import com.developer.copilot.chatassistant.redis.repository.ChatAssistantRedisRepository;
import com.developer.copilot.chatassistant.redis.service.ChatAssistantRedisService;
import com.developer.copilot.chatassistant.redis.service.impl.ChatAssistantRedisServiceImpl;

/**
 * Chat-assistant-owned Redis beans. Created only when {@code app.chatassistant.redis.enabled=true}.
 */
@Configuration
@EnableConfigurationProperties(ChatAssistantRedisProperties.class)
public class ChatAssistantRedisConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "app.chatassistant.redis", name = "enabled", havingValue = "true")
    static class Enabled {

        @Bean
        ChatAssistantRedisKeyBuilder chatAssistantRedisKeyBuilder(ChatAssistantRedisProperties properties) {
            return new ChatAssistantRedisKeyBuilder(properties.getKeyPrefix());
        }

        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory chatAssistantRedisConnectionFactory(ChatAssistantRedisProperties properties) {
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
        StringRedisTemplate chatAssistantStringRedisTemplate(
                LettuceConnectionFactory chatAssistantRedisConnectionFactory) {
            return new StringRedisTemplate(chatAssistantRedisConnectionFactory);
        }

        @Bean
        ChatAssistantRedisRepository chatAssistantRedisRepository(
                StringRedisTemplate chatAssistantStringRedisTemplate) {
            return new ChatAssistantRedisRepository(chatAssistantStringRedisTemplate);
        }

        @Bean
        ChatAssistantRedisService chatAssistantRedisService(
                ChatAssistantRedisRepository chatAssistantRedisRepository,
                ChatAssistantRedisKeyBuilder chatAssistantRedisKeyBuilder) {
            return new ChatAssistantRedisServiceImpl(chatAssistantRedisRepository, chatAssistantRedisKeyBuilder);
        }
    }
}

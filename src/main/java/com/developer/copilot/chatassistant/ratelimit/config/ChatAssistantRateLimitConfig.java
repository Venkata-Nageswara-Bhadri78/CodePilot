package com.developer.copilot.chatassistant.ratelimit.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.developer.copilot.chatassistant.ratelimit.filter.ChatAssistantRateLimitFilter;
import com.developer.copilot.chatassistant.ratelimit.service.ChatAssistantRateLimitService;
import com.developer.copilot.chatassistant.ratelimit.service.impl.ChatAssistantRateLimitServiceImpl;
import com.developer.copilot.chatassistant.redis.service.ChatAssistantRedisService;

@Configuration
@EnableConfigurationProperties(ChatAssistantRateLimitProperties.class)
public class ChatAssistantRateLimitConfig {

    /**
     * After {@code springSecurityFilterChain} (default order -100) so JWT has already
     * populated the principal. Not added to the security chain (avoids double-counting).
     */
    private static final int AFTER_SPRING_SECURITY = -80;

    @Bean
    public ChatAssistantRateLimitService chatAssistantRateLimitService(
            ObjectProvider<ChatAssistantRedisService> redisService) {
        return new ChatAssistantRateLimitServiceImpl(redisService.getIfAvailable());
    }

    @Bean
    public ChatAssistantRateLimitFilter chatAssistantRateLimitFilter(
            ChatAssistantRateLimitProperties properties,
            ChatAssistantRateLimitService chatAssistantRateLimitService) {
        return new ChatAssistantRateLimitFilter(properties, chatAssistantRateLimitService);
    }

    @Bean
    public FilterRegistrationBean<ChatAssistantRateLimitFilter> chatAssistantRateLimitFilterRegistration(
            ChatAssistantRateLimitFilter chatAssistantRateLimitFilter) {
        FilterRegistrationBean<ChatAssistantRateLimitFilter> registration =
                new FilterRegistrationBean<>(chatAssistantRateLimitFilter);
        registration.addUrlPatterns("/api/v1/chat-assistant", "/api/v1/chat-assistant/*");
        registration.setOrder(AFTER_SPRING_SECURITY);
        registration.setEnabled(true);
        return registration;
    }
}

package com.developer.copilot.chatassistant.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.chatassistant")
public class ChatAssistantRateLimitProperties {

    /** POST /api/v1/chat-assistant/jobs/{id}/messages per identity per minute. */
    private int messagesPerMinute = 8;
}

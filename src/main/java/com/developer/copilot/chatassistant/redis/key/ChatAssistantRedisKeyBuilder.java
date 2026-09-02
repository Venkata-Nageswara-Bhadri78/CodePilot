package com.developer.copilot.chatassistant.redis.key;

import java.util.Locale;

/**
 * Builds namespaced Redis keys for chat-assistant rate-limit counters.
 * Identity colons are stripped so IPv6 cannot collide with the key delimiter.
 */
public class ChatAssistantRedisKeyBuilder {

    private final String prefix;

    public ChatAssistantRedisKeyBuilder(String prefix) {
        this.prefix = prefix == null || prefix.isBlank() ? "chatassistant" : prefix.trim();
    }

    public String build(String namespace, String identity) {
        return prefix + ":" + sanitize(namespace) + ":" + sanitize(identity);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(':', '_');
    }
}

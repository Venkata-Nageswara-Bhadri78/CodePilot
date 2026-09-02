package com.developer.copilot.ai.redis.key;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AiRedisKeyBuilderTest {

    @Test
    void build_sanitizesColonsAndLowercases() {
        AiRedisKeyBuilder builder = new AiRedisKeyBuilder("ai");
        assertEquals("ai:rl-chat_ip:2001_db8__1", builder.build("rl-chat:ip", "2001:db8::1"));
    }

    @Test
    void build_blankIdentity_isUnknown() {
        AiRedisKeyBuilder builder = new AiRedisKeyBuilder("ai");
        assertEquals("ai:rl-chat:unknown", builder.build("rl-chat", "  "));
    }
}

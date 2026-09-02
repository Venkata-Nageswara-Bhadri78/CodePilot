package com.developer.copilot.user.redis.key;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserRedisKeyBuilderTest {

    @Test
    void build_sanitizesColonsAndLowercases() {
        UserRedisKeyBuilder builder = new UserRedisKeyBuilder("user");
        assertEquals("user:rl-upload_ip:2001_db8__1", builder.build("rl-upload:ip", "2001:db8::1"));
    }

    @Test
    void build_blankIdentity_isUnknown() {
        UserRedisKeyBuilder builder = new UserRedisKeyBuilder("user");
        assertEquals("user:rl-upload:unknown", builder.build("rl-upload", "  "));
    }
}

package com.developer.copilot.auth.redis.key;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AuthRedisKeyBuilderTest {

    @Test
    void build_sanitizesColonsAndLowercases() {
        AuthRedisKeyBuilder builder = new AuthRedisKeyBuilder("auth");
        assertEquals("auth:rl-login_ip:2001_db8__1", builder.build("rl-login:ip", "2001:db8::1"));
    }

    @Test
    void build_blankIdentity_isUnknown() {
        AuthRedisKeyBuilder builder = new AuthRedisKeyBuilder("auth");
        assertEquals("auth:mail:unknown", builder.build("mail", "  "));
    }
}

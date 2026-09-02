package com.developer.copilot.common.redis.key;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CommonRedisKeyBuilderTest {

    @Test
    void build_sanitizesColonsAndLowercases() {
        CommonRedisKeyBuilder builder = new CommonRedisKeyBuilder("common");
        assertEquals("common:rl-internal_key:2001_db8__1", builder.build("rl-internal:key", "2001:db8::1"));
    }

    @Test
    void build_blankIdentity_isUnknown() {
        CommonRedisKeyBuilder builder = new CommonRedisKeyBuilder("common");
        assertEquals("common:rl-internal:unknown", builder.build("rl-internal", "  "));
    }
}

package com.developer.copilot.jobs.redis.key;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JobsRedisKeyBuilderTest {

    @Test
    void build_sanitizesColonsAndLowercases() {
        JobsRedisKeyBuilder builder = new JobsRedisKeyBuilder("jobs");
        assertEquals("jobs:rl-post_ip:2001_db8__1", builder.build("rl-post:ip", "2001:db8::1"));
    }

    @Test
    void build_blankIdentity_isUnknown() {
        JobsRedisKeyBuilder builder = new JobsRedisKeyBuilder("jobs");
        assertEquals("jobs:rl-post:unknown", builder.build("rl-post", "  "));
    }
}

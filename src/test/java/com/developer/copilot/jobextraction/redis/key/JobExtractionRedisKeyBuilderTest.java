package com.developer.copilot.jobextraction.redis.key;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JobExtractionRedisKeyBuilderTest {

    @Test
    void build_sanitizesColonsAndLowercases() {
        JobExtractionRedisKeyBuilder builder = new JobExtractionRedisKeyBuilder("jobextraction");
        assertEquals("jobextraction:rl-parse_ip:2001_db8__1", builder.build("rl-parse:ip", "2001:db8::1"));
    }

    @Test
    void build_blankIdentity_isUnknown() {
        JobExtractionRedisKeyBuilder builder = new JobExtractionRedisKeyBuilder("jobextraction");
        assertEquals("jobextraction:rl-parse:unknown", builder.build("rl-parse", "  "));
    }
}

package com.developer.copilot.jobextraction.automatedjobextraction.redis.key;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AutomatedJobExtractionRedisKeyBuilderTest {

    @Test
    void build_sanitizesColonsAndLowercases() {
        AutomatedJobExtractionRedisKeyBuilder builder =
                new AutomatedJobExtractionRedisKeyBuilder("automatedjobextraction");
        assertEquals("automatedjobextraction:rl-parse_ip:2001_db8__1",
                builder.build("rl-parse:ip", "2001:db8::1"));
    }

    @Test
    void build_blankIdentity_isUnknown() {
        AutomatedJobExtractionRedisKeyBuilder builder =
                new AutomatedJobExtractionRedisKeyBuilder("automatedjobextraction");
        assertEquals("automatedjobextraction:rl-parse:unknown", builder.build("rl-parse", "  "));
    }

    @Test
    void build_blankPrefix_usesDefault() {
        AutomatedJobExtractionRedisKeyBuilder builder = new AutomatedJobExtractionRedisKeyBuilder("  ");
        assertEquals("automatedjobextraction:rl:7", builder.build("rl", "7"));
    }
}

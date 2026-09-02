package com.developer.copilot.ai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.developer.copilot.ai.dto.request.AiMode;

class AiPropertiesTest {

    @Test
    void setStreamingTimeoutSeconds_updatesTimeoutSeconds() {
        AiProperties properties = new AiProperties();
        properties.setStreamingTimeoutSeconds(30);

        assertEquals(30, properties.getTimeoutSeconds());
        assertEquals(30, properties.getStreamingTimeoutSeconds());
    }

    @Test
    void maxTokensFor_coverLetterUsesHigherCap() {
        AiProperties properties = new AiProperties();

        assertEquals(4096, properties.maxTokensFor(AiMode.COVER_LETTER));
        assertEquals(2048, properties.maxTokensFor(AiMode.GENERAL_CHAT));
        assertEquals(2048, properties.maxTokensFor(AiMode.MATCH_ANALYSIS));
    }
}

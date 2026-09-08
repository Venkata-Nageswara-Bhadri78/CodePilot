package com.developer.copilot.jobextraction.automatedjobextraction.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.developer.copilot.jobextraction.automatedjobextraction.exception.AutomatedJobPageFetchException;

class JdkJobPageHttpClientTest {

    @Test
    void readLimited_withinCap() throws Exception {
        byte[] body = JdkJobPageHttpClient.readLimited(
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), 10);
        assertEquals("hello", new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void readLimited_exceedsCap_throws() {
        assertThrows(AutomatedJobPageFetchException.class, () -> JdkJobPageHttpClient.readLimited(
                new ByteArrayInputStream("0123456789abcdef".getBytes(StandardCharsets.UTF_8)), 8));
    }

    @Test
    void readLimited_nullStream_empty() throws Exception {
        byte[] body = JdkJobPageHttpClient.readLimited(null, 10);
        assertEquals(0, body.length);
    }
}

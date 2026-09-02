package com.developer.copilot.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void builder_producesCorrectFieldValues() {
        LocalDateTime now = LocalDateTime.now();

        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("ok")
                .data("payload")
                .timestamp(now)
                .build();

        assertTrue(response.isSuccess());
        assertEquals("ok", response.getMessage());
        assertEquals("payload", response.getData());
        assertEquals(now, response.getTimestamp());
    }

    @Test
    void builder_allowsNullDataForVoidResponses() {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message("failed")
                .timestamp(LocalDateTime.now())
                .build();

        assertFalse(response.isSuccess());
        assertNull(response.getData());
    }

    @Test
    void builder_failedResponseStillHasTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message("failed")
                .timestamp(now)
                .build();

        assertFalse(response.isSuccess());
        assertEquals(now, response.getTimestamp());
    }
}

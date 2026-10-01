package com.developer.copilot.chatassistant.exception;

import com.developer.copilot.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CustomPromptExceptionMappingTest {

    private final CustomPromptExceptionHandler handler = new CustomPromptExceptionHandler();

    @Test
    void notFound_is404WithStableMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotFound(new CustomPromptNotFoundException());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Custom prompt not found.", response.getBody().getMessage());
    }
}

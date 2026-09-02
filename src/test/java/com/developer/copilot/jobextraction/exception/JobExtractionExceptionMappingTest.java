package com.developer.copilot.jobextraction.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.common.exception.GlobalExceptionHandler;

class JobExtractionExceptionMappingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void emailNotVerified_is403() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleEmailNotVerified(new EmailNotVerifiedException());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Please verify your email before using this feature.", response.getBody().getMessage());
    }

    @Test
    void aiUnavailable_is503() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleJobExtractionAiUnavailable(
                new JobExtractionAiUnavailableException("The AI service is temporarily unavailable. Please try again shortly."));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("The AI service is temporarily unavailable. Please try again shortly.",
                response.getBody().getMessage());
    }

    @Test
    void parseRateLimit_is429WithRetryAfter() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleJobExtractionRateLimitExceeded(
                new com.developer.copilot.jobextraction.ratelimit.exception.RateLimitExceededException(9));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("9", response.getHeaders().getFirst("Retry-After"));
    }
}

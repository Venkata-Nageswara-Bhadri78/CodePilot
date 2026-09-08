package com.developer.copilot.jobextraction.automatedjobextraction.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.common.exception.GlobalExceptionHandler;

class AutomatedJobExtractionExceptionMappingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void invalidAutomatedJobUrl_is400WithFixedMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleInvalidAutomatedJobUrl(new InvalidAutomatedJobUrlException());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("INVALID JOB URL", response.getBody().getMessage());
    }

    @Test
    void fetchFailure_is502WithGenericMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAutomatedJobPageFetch(new AutomatedJobPageFetchException());

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(AutomatedJobPageFetchException.MESSAGE, response.getBody().getMessage());
    }

    @Test
    void unavailable_is503() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAutomatedJobExtractionUnavailable(
                new AutomatedJobExtractionUnavailableException("The job page could not be retrieved. Please try again shortly."));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void parseRateLimit_is429WithRetryAfter() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAutomatedJobExtractionRateLimitExceeded(
                new com.developer.copilot.jobextraction.automatedjobextraction.ratelimit.exception.RateLimitExceededException(11));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("11", response.getHeaders().getFirst("Retry-After"));
    }
}

package com.developer.copilot.jobs.exception;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.common.exception.InvalidJobUrlException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JobsExceptionMappingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void jobNotFound_is404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleJobNotFound(new JobNotFoundException("Job not found with id: 9"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Job not found with id: 9", response.getBody().getMessage());
    }

    @Test
    void duplicateJob_is409() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDuplicateJob(new DuplicateJobException("This post was already added to your records."));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("This post was already added to your records.", response.getBody().getMessage());
    }

    @Test
    void jobValidation_is400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleJobValidation(new JobValidationException("size must be between 1 and 50."));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void invalidJobUrl_is400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleInvalidJobUrl(new InvalidJobUrlException("Job URL must be a valid absolute http/https URL"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void dataIntegrity_isGeneric409() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException("SQL detail"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("The request conflicts with existing data. Please retry.", response.getBody().getMessage());
    }
}

package com.developer.copilot.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.developer.copilot.ai.exception.AiResumePendingException;
import com.developer.copilot.ai.exception.AiServiceException;
import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.common.storage.exception.StorageException;
import com.developer.copilot.jobs.exception.JobNotFoundException;
import com.developer.copilot.user.exception.ResumeNotFoundException;
import com.developer.copilot.user.exception.ResumeParsingException;

class GlobalExceptionHandlerAiTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void aiProviderError_mapsTo502() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAiServiceException(
                new AiServiceException("An unexpected error occurred while communicating with the AI model. Please try again."));

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(false, response.getBody().isSuccess());
    }

    @Test
    void missingJob_mapsTo404() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleJobNotFound(new JobNotFoundException("Job not found."));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void missingResume_mapsTo404() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResumeNotFound(new ResumeNotFoundException());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void pendingResume_mapsTo409() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAiResumePending(
                new AiResumePendingException("Your resume is still being processed. Please try again in a few moments."));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void parseFailure_mapsTo422() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResumeParsing(
                new ResumeParsingException("Your resume could not be parsed."));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
    }

    @Test
    void storageError_hidesTechnicalDetails() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleStorage(
                new StorageException("Failed against bucket=secret-bucket endpoint=minio:9000"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("A file storage error occurred. Please try again later.", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("secret-bucket"));
    }
}

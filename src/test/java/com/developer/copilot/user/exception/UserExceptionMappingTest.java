package com.developer.copilot.user.exception;

import com.developer.copilot.common.exception.GlobalExceptionHandler;
import com.developer.copilot.user.config.ResumeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserExceptionMappingTest {

    @Test
    void maxUploadSizeExceeded_is400WithConfiguredCap() {
        ResumeProperties properties = new ResumeProperties();
        properties.setMaxFileSizeMb(5);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(properties);

        var response = handler.handleMultipartTooLarge(new MaxUploadSizeExceededException(1));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Maximum file size is 5 MB.", response.getBody().getMessage());
    }

    @Test
    void profileItemLimit_is400() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleProfileItemLimit(
                new ProfileItemLimitExceededException("work experience", 20));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Maximum of 20 work experience records allowed.", response.getBody().getMessage());
    }

    @Test
    void resumeParsing_pending_is422() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleResumeParsing(
                new ResumeParsingException("Resume parsing is still in progress. Please retry shortly."));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("still in progress"));
    }
}

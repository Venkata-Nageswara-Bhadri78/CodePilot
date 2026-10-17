package com.developer.copilot.community.exception;

import java.time.LocalDateTime;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.developer.copilot.common.dto.ApiResponse;
import com.developer.copilot.community.controller.CommunityAccountController;
import com.developer.copilot.community.dto.CommunityConsentMessages;

import tools.jackson.databind.exc.UnrecognizedPropertyException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CommunityAccountController.class)
public class CommunityAccountExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        if (hasCause(ex, UnrecognizedPropertyException.class)
                || hasCause(ex, com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class)
                || hasCause(ex, CommunityConsentRejectedException.class)) {
            return error(HttpStatus.BAD_REQUEST, CommunityConsentMessages.UNKNOWN_FIELD);
        }
        return error(HttpStatus.BAD_REQUEST, "Request body is missing or malformed JSON.");
    }

    private static boolean hasCause(Throwable ex, Class<?> type) {
        Throwable current = ex;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @ExceptionHandler(CommunityConsentRejectedException.class)
    public ResponseEntity<ApiResponse<Void>> handleRejected(CommunityConsentRejectedException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CommunityTermsVersionException.class)
    public ResponseEntity<ApiResponse<Void>> handleVersion(CommunityTermsVersionException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CommunityAccountRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountRequired(CommunityAccountRequiredException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(TermsAcceptanceRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleTermsRequired(TermsAcceptanceRequiredException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(
                ApiResponse.<Void>builder()
                        .success(false)
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}

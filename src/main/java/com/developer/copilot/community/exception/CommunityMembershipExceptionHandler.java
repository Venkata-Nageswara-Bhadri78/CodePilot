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
import com.developer.copilot.community.controller.CommunityMembershipController;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CommunityMembershipController.class)
public class CommunityMembershipExceptionHandler {

    @ExceptionHandler(CommunityValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(CommunityValidationException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        CommunityValidationException validation = findCause(ex, CommunityValidationException.class);
        if (validation != null) {
            return error(HttpStatus.BAD_REQUEST, validation.getMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "Request body is missing or malformed JSON.");
    }

    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        Throwable current = ex;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(
                ApiResponse.<Void>builder()
                        .success(false)
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(CommunityUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnavailable(CommunityUnavailableException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CommunityMembershipRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleMembershipRequired(CommunityMembershipRequiredException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }
}

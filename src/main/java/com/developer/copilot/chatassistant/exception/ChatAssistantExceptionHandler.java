package com.developer.copilot.chatassistant.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.developer.copilot.chatassistant.controller.ChatAssistantController;
import com.developer.copilot.common.dto.ApiResponse;

import jakarta.validation.ConstraintViolationException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ChatAssistantController.class)
public class ChatAssistantExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException unused) {
        return error(HttpStatus.BAD_REQUEST, "Invalid job id.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining(", "));
        if (message.isBlank()) {
            message = "Invalid request parameter.";
        }
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ChatConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ChatConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
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

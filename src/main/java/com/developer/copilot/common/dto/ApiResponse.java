package com.developer.copilot.common.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * Uniform response envelope returned by every endpoint in the application. Registered once
 * as a shared "ApiResponse" schema component (see {@code SwaggerConfig}) so every endpoint's
 * generated docs reference the same schema instead of springdoc regenerating a slightly
 * different generic-typed shape per endpoint.
 */
@Getter
@Builder
@Schema(name = "ApiResponse", description = "Uniform response envelope used by every endpoint in the API.")
public class ApiResponse<T> {

    @Schema(description = "Whether the request completed successfully.", example = "true")
    private boolean success;

    @Schema(description = "Human-readable summary of the result, always safe to display to a client.",
            example = "Request completed successfully.")
    private String message;

    @Schema(description = "The response payload. Null for endpoints that don't return data or on error.")
    private T data;

    @Schema(description = "Server LocalDateTime at which the response was produced (no timezone offset).")
    private LocalDateTime timestamp;

}
package com.developer.copilot.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "Authentication tokens returned after login or refresh")
@Getter
@AllArgsConstructor
public class AuthResponse {

    @Schema(description = "JWT access token. Send as Authorization: Bearer <token>. Default lifetime 15 minutes.",
            example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;

    @Schema(description = "Opaque refresh UUID, not a JWT", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
    private String refreshToken;
}

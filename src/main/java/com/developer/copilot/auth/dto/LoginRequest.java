package com.developer.copilot.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "Request payload for user login")
@Getter
@Setter
public class LoginRequest {
    @Schema(description = "Registered email address", example = "john.doe@example.com")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "Account password", example = "Secure@123")
    @NotBlank
    private String password;
}

package com.developer.copilot.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "Request payload for user login")
@Getter
@Setter
public class LoginRequest {
    @Schema(description = "Registered email address", example = "john.doe@example.com")
    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Schema(description = "Account password", example = "Secure@123", format = "password")
    @NotBlank
    private String password;
}

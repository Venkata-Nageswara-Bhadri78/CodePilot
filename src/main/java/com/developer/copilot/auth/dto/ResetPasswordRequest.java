package com.developer.copilot.auth.dto;

import com.developer.copilot.auth.validation.ValidPassword;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Set a new password with the UUID from the reset email. Invalidates all access JWTs and refresh tokens.")
public class ResetPasswordRequest {

    @NotBlank
    @Schema(description = "Raw reset UUID from email, not the stored hash", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
    private String token;

    @ValidPassword
    @Schema(description = "8-72 chars with upper, lower, number and special character", example = "Secure@123",
            format = "password", minLength = 8, maxLength = 72)
    private String newPassword;
}

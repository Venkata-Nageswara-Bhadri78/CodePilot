package com.developer.copilot.auth.dto;

import com.developer.copilot.auth.validation.ValidPassword;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "OTP from the password-setup email plus the password to store.")
public class SetupPasswordConfirmRequest {

    @NotBlank
    @Pattern(regexp = "^\\d{6}$")
    @Schema(description = "6-digit one-time code", example = "123456", pattern = "^\\d{6}$")
    private String otp;

    @ValidPassword
    @Schema(description = "8-72 chars with upper, lower, number and special character", example = "Secure@123",
            format = "password", minLength = 8, maxLength = 72)
    private String newPassword;
}

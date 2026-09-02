package com.developer.copilot.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Email plus the 6-digit OTP from the verification mail")
public class VerifyOtpRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    @Schema(description = "Account email", example = "john.doe@example.com")
    private String email;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$")
    @Schema(description = "6-digit one-time code", example = "123456", pattern = "^\\d{6}$")
    private String otp;
}

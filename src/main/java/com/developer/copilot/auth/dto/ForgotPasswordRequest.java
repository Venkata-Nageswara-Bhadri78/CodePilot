package com.developer.copilot.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Start a password reset. Always returns a generic 200 whether the email exists or not.")
public class ForgotPasswordRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    @Schema(example = "john.doe@example.com")
    private String email;
}

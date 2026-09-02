package com.developer.copilot.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request a new email OTP. Always returns a generic 200.")
public class ResendOtpRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    @Schema(example = "john.doe@example.com")
    private String email;
}

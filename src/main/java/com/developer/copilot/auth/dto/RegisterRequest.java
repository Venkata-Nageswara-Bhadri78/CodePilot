package com.developer.copilot.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import com.developer.copilot.auth.validation.ValidPassword;

@Schema(description = "Request payload for user registration")
@Getter
@Setter
public class RegisterRequest {
    @Schema(description = "Unique username", example = "johndoe")
    @NotBlank
    @Size(min=3, max=50)
    private String username;

    @Schema(description = "User's full name", example = "John Doe")
    @NotBlank
    @Size(min=3, max=100)
    private String fullName;

    @Schema(description = "Valid email address", example = "john.doe@example.com")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "Strong password", example = "Secure@123")
    @ValidPassword
    private String password;
}

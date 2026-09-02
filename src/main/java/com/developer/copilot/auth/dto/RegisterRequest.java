package com.developer.copilot.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import com.developer.copilot.auth.validation.ValidPassword;

@Schema(description = "Request payload for user registration. Email is stored lowercase. Duplicate username/email returns the same 201 as a new account.")
@Getter
@Setter
public class RegisterRequest {
    @Schema(description = "Unique username, stored lowercase", example = "johndoe", minLength = 3, maxLength = 50)
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    @Schema(description = "User's full name", example = "John Doe", minLength = 3, maxLength = 100)
    @NotBlank
    @Size(min = 3, max = 100)
    private String fullName;

    @Schema(description = "Valid email address, stored lowercase", example = "john.doe@example.com")
    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Schema(description = "8-72 chars with upper, lower, number and special character", example = "Secure@123",
            format = "password", minLength = 8, maxLength = 72)
    @ValidPassword
    private String password;
}

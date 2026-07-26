package com.developer.copilot.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResendOtpRequest {

    @NotBlank
    @Email
    private String email;
}
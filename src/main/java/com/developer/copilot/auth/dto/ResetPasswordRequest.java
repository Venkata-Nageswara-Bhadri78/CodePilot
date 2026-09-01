package com.developer.copilot.auth.dto;

import com.developer.copilot.auth.validation.ValidPassword;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank
    private String token;

    @ValidPassword
    private String newPassword;

}
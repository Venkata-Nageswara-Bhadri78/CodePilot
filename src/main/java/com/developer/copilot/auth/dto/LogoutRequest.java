package com.developer.copilot.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Revoke this device's refresh token. Access JWT still works until it expires (about 15 minutes). Use logout-all to kill access JWTs immediately.")
public class LogoutRequest {

    @NotBlank
    @Schema(description = "Opaque refresh UUID for this session", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
    private String refreshToken;
}

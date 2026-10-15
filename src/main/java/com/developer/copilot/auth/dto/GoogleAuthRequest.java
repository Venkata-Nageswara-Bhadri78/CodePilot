package com.developer.copilot.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Google Identity Services ID token. This is not an application access token.")
public class GoogleAuthRequest {

    @NotBlank
    @Size(max = 4096)
    @Schema(description = "Google ID token JWT from the GIS button", maxLength = 4096)
    private String idToken;
}

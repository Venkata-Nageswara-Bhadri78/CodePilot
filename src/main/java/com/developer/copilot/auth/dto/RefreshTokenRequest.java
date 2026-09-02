package com.developer.copilot.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Rotate the refresh UUID. This path is public; the UUID is the credential. Replaying a rotated token revokes every session.")
public class RefreshTokenRequest {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "Opaque refresh UUID from login, not a JWT", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
    private String refreshToken;
}

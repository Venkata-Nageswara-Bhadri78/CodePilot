package com.developer.copilot.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "Restricted access token for the Chrome browser-extension client. No refresh token is issued.")
@Getter
@AllArgsConstructor
public class ExtensionAuthResponse {

    @Schema(description = "JWT access token scoped to job extraction. Send as Authorization: Bearer <token>.",
            example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;

    @Schema(description = "Server-issued client id encoded in the JWT cid claim", example = "browser-extension")
    private String client;

    @Schema(description = "Access token lifetime in seconds", example = "900")
    private long expiresIn;
}

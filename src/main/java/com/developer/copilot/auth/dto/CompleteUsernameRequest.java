package com.developer.copilot.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Username chosen after Google sign-in. Stored lowercase.")
public class CompleteUsernameRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    @Schema(description = "Unique username, stored lowercase", example = "johndoe", minLength = 3, maxLength = 50)
    private String username;
}

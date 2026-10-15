package com.developer.copilot.auth.dto;

import com.developer.copilot.auth.enums.Role;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Public identity of the current user. Password, tokenVersion and enabled flags are never returned.")
public class UserResponse {

    @Schema(description = "User id", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "Username", example = "johndoe", accessMode = Schema.AccessMode.READ_ONLY)
    private String username;

    @Schema(description = "Full name", example = "John Doe", accessMode = Schema.AccessMode.READ_ONLY)
    private String fullName;

    @Schema(description = "Email", example = "john.doe@example.com", accessMode = Schema.AccessMode.READ_ONLY)
    private String email;

    @Schema(description = "Role copied from the account", example = "USER", accessMode = Schema.AccessMode.READ_ONLY)
    private Role role;

    @Schema(description = "True when the account has a password and can also use email/password login.",
            example = "true", accessMode = Schema.AccessMode.READ_ONLY)
    private boolean passwordConfigured;

    @Schema(description = "True when a Google identity is linked to this account.",
            example = "false", accessMode = Schema.AccessMode.READ_ONLY)
    private boolean googleLinked;
}

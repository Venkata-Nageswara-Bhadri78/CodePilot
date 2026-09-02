package com.developer.copilot.auth.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.auth.dto.AuthResponse;
import com.developer.copilot.auth.dto.ForgotPasswordRequest;
import com.developer.copilot.auth.dto.LoginRequest;
import com.developer.copilot.auth.dto.LogoutRequest;
import com.developer.copilot.auth.dto.RefreshTokenRequest;
import com.developer.copilot.auth.dto.RegisterRequest;
import com.developer.copilot.auth.dto.ResendOtpRequest;
import com.developer.copilot.auth.dto.ResetPasswordRequest;
import com.developer.copilot.auth.dto.UserResponse;
import com.developer.copilot.auth.dto.VerifyOtpRequest;
import com.developer.copilot.auth.service.AuthService;
import com.developer.copilot.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(
        name = "Authentication",
        description = "Identity for the rest of the API. Flow: register → OTP email → verify-email → login "
                + "→ Authorize with the access JWT → GET /me. Access JWT lasts about 15 minutes. "
                + "Refresh is a UUID (not a JWT) on POST /refresh-token; rotation plus reuse detection applies. "
                + "Accounts stay disabled until verify-email. Login failures always return "
                + "\"Invalid email or password.\" Missing JWT on protected routes returns \"Unauthorized.\" "
                + "POST /logout kills that refresh only; POST /logout-all and reset-password also bump tokenVersion "
                + "so old access JWTs die immediately.")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {
    private static final String BEARER_AUTH = "Bearer Authentication";

    private final AuthService authService;

    @Operation(
            summary = "Register a new user",
            description = "Creates an unverified account and emails an OTP when the username and email are new. "
                    + "Duplicate username/email still return 201 with the same message so existence is not leaked. "
                    + "OTP is never in the JSON body.",
            security = {})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User registered successfully."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {

        authService.register(request);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
            .success(true)
            .message("User registered successfully.")
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Log in to the system",
            description = "Returns a JWT access token (Authorization: Bearer) and an opaque refresh UUID. "
                    + "Unknown user, wrong password, unverified and disabled accounts all return 401 "
                    + "\"Invalid email or password.\"",
            security = {})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Invalid email or password.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {

        AuthResponse authResponse = authService.login(request);

        ApiResponse<AuthResponse> response = ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Login successful.")
                .data(authResponse)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Verify email via OTP",
            description = "Activates the account. 400 messages: OTP not found, already used, expired, or invalid.",
            security = {})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Email verified successfully."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid, expired, used, or missing OTP / OTP not 6 digits",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyOtpRequest request) {

        authService.verifyOtp(request);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Email verified successfully.")
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @Operation(
            summary = "Resend verification OTP",
            description = "Always 200 with the generic sentence. Mail is sent only for an existing unverified account, with a cooldown.",
            security = {})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "If the account requires verification, an OTP has been sent."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {

        authService.resendOtp(request);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("If the account requires verification, an OTP has been sent.")
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @Operation(
            summary = "Request password reset",
            description = "Always 200: If the account exists, a password reset email has been sent.",
            security = {})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "If the account exists, a password reset email has been sent."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("If the account exists, a password reset email has been sent.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @Operation(
            summary = "Reset password",
            description = "Sets a new password, bumps tokenVersion (all access JWTs die), and revokes refresh tokens.",
            security = {})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successfully."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid, used, or expired reset token, or weak password",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Password reset successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @Operation(summary = "Get current user profile", security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current user."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized. Missing/invalid JWT uses this message, not the login 401.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me() {

        return ResponseEntity.ok(
                ApiResponse.<UserResponse>builder()
                        .success(true)
                        .message("Current user.")
                        .data(authService.me())
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @Operation(
            summary = "Refresh access token",
            description = "Public at the filter; the refresh UUID is the secret. Rotates the token. "
                    + "Replaying an already-rotated token revokes every session for that user.",
            security = {})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Invalid, expired, or revoked refresh token",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request){
        AuthResponse authResponse = authService.refreshToken(request);

        return ResponseEntity.ok(
                ApiResponse.<AuthResponse>builder()
                        .success(true)
                        .message("Token refreshed successfully.")
                        .data(authResponse)
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @Operation(
            summary = "Logout current session",
            description = "Requires a live access JWT plus this session's refresh UUID. Does not bump tokenVersion.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out successfully."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized JWT or invalid refresh token",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Logged out successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    @Operation(
            summary = "Logout from all devices",
            description = "Bumps tokenVersion and revokes every refresh token so access JWTs die on the next request.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out from all devices successfully."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAllDevices() {
        authService.logoutAllDevices();

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Logged out from all devices successfully.")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }
}

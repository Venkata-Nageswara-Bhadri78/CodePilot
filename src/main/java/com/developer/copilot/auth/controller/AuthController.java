package com.developer.copilot.auth.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.developer.copilot.auth.dto.AuthResponse;
import com.developer.copilot.auth.dto.CompleteUsernameRequest;
import com.developer.copilot.auth.dto.ExtensionAuthResponse;
import com.developer.copilot.auth.dto.ForgotPasswordRequest;
import com.developer.copilot.auth.dto.GoogleAuthRequest;
import com.developer.copilot.auth.dto.LoginRequest;
import com.developer.copilot.auth.dto.LogoutRequest;
import com.developer.copilot.auth.dto.RefreshTokenRequest;
import com.developer.copilot.auth.dto.RegisterRequest;
import com.developer.copilot.auth.dto.ResendOtpRequest;
import com.developer.copilot.auth.dto.ResetPasswordRequest;
import com.developer.copilot.auth.dto.SetupPasswordConfirmRequest;
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
        description = "Identity for the rest of the API. Email/password flow: register → OTP email → verify-email → login. "
                + "Google flow: POST /google with a Google ID token, then POST /complete-username when onboardingComplete is false. "
                + "A Google account can later set a password with POST /setup-password/request and /setup-password/confirm. "
                + "Access JWT lasts about 15 minutes. Refresh is a UUID (not a JWT) on POST /refresh-token. "
                + "Accounts stay disabled until verify-email, except Google users whose email Google already verified. "
                + "Login failures always return \"Invalid email or password.\" Missing JWT on protected routes returns \"Unauthorized.\" "
                + "Users without a username cannot call dashboard or job APIs. "
                + "POST /logout kills that refresh only; POST /logout-all and reset-password also bump tokenVersion "
                + "so old access JWTs die immediately. An already-authenticated web session with a username can mint a restricted "
                + "browser-extension access JWT at POST /extension-token (job extraction only; no refresh token).")
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
            summary = "Continue with Google",
            description = "Verifies a Google Identity Services ID token and signs in or creates the user. "
                    + "New users have no username until POST /complete-username. "
                    + "Does not link Google to an existing email/password account. "
                    + "The Google token is not an application bearer token.",
            security = {})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Login successful, or username still required."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Invalid Google token, or the linked account is disabled.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Email already registered without this Google identity.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse authResponse = authService.googleLogin(request);
        String message = authResponse.isOnboardingComplete()
                ? "Login successful."
                : "Complete your username to continue.";

        return ResponseEntity.ok(
                ApiResponse.<AuthResponse>builder()
                        .success(true)
                        .message(message)
                        .data(authResponse)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @Operation(
            summary = "Link Google to the signed-in account",
            description = "Requires a web access JWT for an account that already has a username. "
                    + "Verifies the Google ID token and stores that Google subject on this user. "
                    + "Does not create a user. The Google email must match this account. "
                    + "Public POST /google still does not link by email.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Google account linked successfully."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized, or invalid Google token.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Extension token, or username not set yet.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Email mismatch, Google already linked elsewhere, or this account already has a different Google identity.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/google/link")
    public ResponseEntity<ApiResponse<Void>> linkGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        authService.linkGoogle(request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Google account linked successfully.")
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @Operation(
            summary = "Save the username for a Google account",
            description = "Requires a web access JWT. Allowed while onboarding is incomplete. "
                    + "A taken username returns 409. Submitting the username already stored returns 200. "
                    + "A different username after one is saved returns 409.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Username saved successfully."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Browser-extension token.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Username taken or already set to a different value.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/complete-username")
    public ResponseEntity<ApiResponse<AuthResponse>> completeUsername(
            @Valid @RequestBody CompleteUsernameRequest request) {
        AuthResponse authResponse = authService.completeUsername(request);
        return ResponseEntity.ok(
                ApiResponse.<AuthResponse>builder()
                        .success(true)
                        .message("Username saved successfully.")
                        .data(authResponse)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @Operation(
            summary = "Email an OTP to set or replace the account password",
            description = "For a signed-in web user who already has a username. Confirms the address with a 6-digit code. "
                    + "After confirm, the same account can use Google and email/password.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "A verification code has been sent to your email."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Extension token, or username not set yet.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/setup-password/request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordSetup() {
        boolean sent = authService.requestPasswordSetup();
        String message = sent
                ? "A verification code has been sent to your email."
                : "A verification code was sent recently. Use that code or wait before requesting another.";
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @Operation(
            summary = "Set the account password after the email OTP",
            description = "Stores a BCrypt password, bumps tokenVersion, revokes refresh tokens, and returns a new session. "
                    + "Google sign-in for the same account keeps working.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password saved successfully."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Invalid, expired, or used OTP, or weak password",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Extension token, or username not set yet.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/setup-password/confirm")
    public ResponseEntity<ApiResponse<AuthResponse>> confirmPasswordSetup(
            @Valid @RequestBody SetupPasswordConfirmRequest request) {
        AuthResponse authResponse = authService.confirmPasswordSetup(request);
        return ResponseEntity.ok(
                ApiResponse.<AuthResponse>builder()
                        .success(true)
                        .message("Password saved successfully.")
                        .data(authResponse)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @Operation(
            summary = "Issue a browser-extension access token",
            description = "Requires a live web-frontend access JWT for a user who already has a username. "
                    + "Returns a short-lived JWT whose cid claim is browser-extension. That token may call only "
                    + "job-extraction endpoints. No refresh token is issued. Incomplete Google users are rejected. "
                    + "logout-all / reset-password still invalidate it via tokenVersion.",
            security = @SecurityRequirement(name = BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Browser extension access token issued."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Extension client, or extension access disabled.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/extension-token")
    public ResponseEntity<ApiResponse<ExtensionAuthResponse>> issueExtensionToken() {
        ExtensionAuthResponse authResponse = authService.issueExtensionToken();

        return ResponseEntity.ok(
                ApiResponse.<ExtensionAuthResponse>builder()
                        .success(true)
                        .message("Browser extension access token issued.")
                        .data(authResponse)
                        .timestamp(LocalDateTime.now())
                        .build());
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

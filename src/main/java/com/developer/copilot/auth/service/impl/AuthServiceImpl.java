package com.developer.copilot.auth.service.impl;

import com.developer.copilot.auth.repository.EmailVerificationRepository;
import com.developer.copilot.auth.repository.PasswordResetTokenRepository;
import com.developer.copilot.auth.repository.RefreshTokenRepository;
import com.developer.copilot.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.developer.copilot.auth.config.AuthProperties;
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
import com.developer.copilot.auth.entity.EmailVerification;
import com.developer.copilot.auth.entity.PasswordResetToken;
import com.developer.copilot.auth.entity.RefreshToken;
import com.developer.copilot.auth.entity.User;
import com.developer.copilot.auth.enums.Role;
import com.developer.copilot.auth.exception.EmailDeliveryException;
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.mapper.AuthMapper;
import com.developer.copilot.auth.ratelimit.service.AuthRateLimitService;
import com.developer.copilot.auth.service.AuthService;
import com.developer.copilot.auth.service.EmailService;
import com.developer.copilot.auth.util.CredentialDigests;
import com.developer.copilot.auth.util.OtpGenerator;
import com.developer.copilot.auth.exception.InvalidOtpException;
import com.developer.copilot.auth.exception.InvalidPasswordResetTokenException;
import com.developer.copilot.auth.exception.InvalidRefreshTokenException;
import com.developer.copilot.auth.exception.OtpExpiredException;
import com.developer.copilot.auth.exception.PasswordResetTokenExpiredException;
import com.developer.copilot.auth.exception.PasswordResetTokenUsedException;
import com.developer.copilot.auth.exception.RefreshTokenExpiredException;
import com.developer.copilot.auth.exception.RefreshTokenRevokedException;
import com.developer.copilot.common.security.CurrentUserService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    static final String INVALID_CREDENTIALS = "Invalid email or password.";

    /**
     * Real BCrypt hash used only so a missing user still pays the same CPU as a wrong password.
     * Corresponds to the password "invalid-dummy-password".
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOahiRIkL5KqXwXz0uV1aYcKq1eN6vW1e";

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthMapper authMapper;
    private final Clock clock;
    private final AuthProperties authProperties;
    private final CurrentUserService currentUserService;
    private final AuthRateLimitService authRateLimitService;

    @Value("${app.jwt.secret}")
    private String otpHmacSecret;

    @Override
    @Transactional
    public void register(RegisterRequest registerRequest) {
        String username = normalizeUsername(registerRequest.getUsername());
        String email = normalizeEmail(registerRequest.getEmail());
        authRateLimitService.consumeOrThrow(
                "register-email", email, authProperties.getRegisterRateLimitPerMinute(), 60);
        String fullName = registerRequest.getFullName() == null ? null : registerRequest.getFullName().trim();

        if (username == null || username.length() < 3 || username.length() > 50) {
            throw new IllegalArgumentException("username: size must be between 3 and 50");
        }

        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            log.debug("Register ignored for existing username or email");
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setRole(Role.USER);
        user.setEnabled(false);
        user.setEmailVerified(false);
        user.setTokenVersion(0);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Register raced on unique username or email");
            return;
        }

        issueAndMailOtp(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        authRateLimitService.consumeOrThrow(
                "login-email", email, authProperties.getLoginRateLimitPerMinute(), 60);

        if (authRateLimitService.isLoginBlocked(email)) {
            passwordEncoder.matches(request.getPassword(), DUMMY_PASSWORD_HASH);
            log.debug("Login blocked by failure window for {}", email);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.getPassword(), DUMMY_PASSWORD_HASH);
            authRateLimitService.recordLoginFailure(email);
            log.debug("Login failed: unknown email");
            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            authRateLimitService.recordLoginFailure(email);
            log.debug("Login failed: bad password");
            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified()) || !Boolean.TRUE.equals(user.getEnabled())) {
            log.debug("Login failed: unverified or disabled account");
            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }

        authRateLimitService.recordLoginSuccess(email);
        return buildAuthResponse(user);
    }

    @Override
    public UserResponse me() {
        return authMapper.toUserResponse(currentUserService.getCurrentUser());
    }

    @Override
    @Transactional
    public void verifyOtp(VerifyOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        authRateLimitService.consumeOrThrow(
                "verify-email", email, authProperties.getVerifyRateLimitPerMinute(), 60);
        EmailVerification verification = emailVerificationRepository
                .findTopByUserEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new InvalidOtpException("OTP not found."));

        User user = verification.getUser();

        if (Boolean.TRUE.equals(verification.getVerified())) {
            throw new InvalidOtpException("OTP already used.");
        }

        if (verification.getExpiresAt().isBefore(now())) {
            throw new OtpExpiredException("OTP has expired.");
        }

        int failedAttempts = verification.getFailedAttempts() == null ? 0 : verification.getFailedAttempts();
        if (failedAttempts >= authProperties.getMaxOtpAttempts()) {
            throw new InvalidOtpException("OTP not found.");
        }

        if (!CredentialDigests.hmacMatches(request.getOtp(), verification.getOtp(), otpHmacSecret)) {
            verification.setFailedAttempts(failedAttempts + 1);
            emailVerificationRepository.save(verification);
            throw new InvalidOtpException("Invalid OTP.");
        }

        verification.setVerified(true);
        user.setEnabled(true);
        user.setEmailVerified(true);

        emailVerificationRepository.save(verification);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void resendOtp(ResendOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        authRateLimitService.consumeOrThrow(
                "resend-email", email, authProperties.getResendRateLimitPerMinute(), 60);
        userRepository.findByEmail(email).ifPresent(user -> {
            if (Boolean.TRUE.equals(user.getEmailVerified())) {
                return;
            }
            if (!authRateLimitService.tryAcquireMail(user.getEmail(), authProperties.getMailCooldownSeconds())) {
                log.debug("Resend OTP skipped by cooldown");
                return;
            }
            issueAndMailOtp(user);
        });
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        authRateLimitService.consumeOrThrow(
                "forgot-email", email, authProperties.getForgotRateLimitPerMinute(), 60);
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!authRateLimitService.tryAcquireMail(
                    "reset:" + user.getEmail(), authProperties.getMailCooldownSeconds())) {
                log.debug("Forgot password skipped by cooldown");
                return;
            }
            passwordResetTokenRepository.deleteByUserIdAndUsedFalse(user.getId());

            String rawToken = UUID.randomUUID().toString();
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(CredentialDigests.sha256(rawToken));
            resetToken.setExpiresAt(now().plusMinutes(authProperties.getResetExpiryMinutes()));
            resetToken.setUsed(false);
            resetToken.setUsedAt(null);

            passwordResetTokenRepository.save(resetToken);

            afterCommit(() -> sendMailSafely(() -> emailService.sendPasswordResetEmail(
                    user.getEmail(),
                    user.getFullName(),
                    rawToken)));
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(CredentialDigests.sha256(request.getToken()))
                .orElseThrow(() -> new InvalidPasswordResetTokenException("Invalid password reset token."));

        if (Boolean.TRUE.equals(resetToken.getUsed())) {
            throw new PasswordResetTokenUsedException("Password reset token is invalid or already used.");
        }

        if (resetToken.getExpiresAt().isBefore(now())) {
            throw new PasswordResetTokenExpiredException("Password reset token has expired.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        bumpTokenVersion(user);
        userRepository.save(user);

        resetToken.setUsed(true);
        resetToken.setUsedAt(now());
        passwordResetTokenRepository.save(resetToken);

        revokeAllRefreshTokens(user);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByTokenForUpdate(CredentialDigests.sha256(request.getRefreshToken()))
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token."));

        User user = storedToken.getUser();

        if (Boolean.TRUE.equals(storedToken.getRevoked())) {
            if (storedToken.getReplacedByToken() != null) {
                revokeAllRefreshTokens(user);
            }
            throw new RefreshTokenRevokedException("Refresh token has been revoked.");
        }

        if (storedToken.getExpiresAt().isBefore(now())) {
            throw new RefreshTokenExpiredException("Refresh token has expired.");
        }

        if (!Boolean.TRUE.equals(user.getEnabled()) || !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new InvalidRefreshTokenException("Invalid refresh token.");
        }

        storedToken.setRevoked(true);

        String newRefreshTokenValue = persistRefreshToken(user, storedToken);
        refreshTokenRepository.save(storedToken);

        String newAccessToken = jwtService.generateToken(user);
        return new AuthResponse(newAccessToken, "Bearer", newRefreshTokenValue);
    }

    private void issueAndMailOtp(User user) {
        String otp = OtpGenerator.generateOtp();
        emailVerificationRepository.deleteByUserId(user.getId());
        EmailVerification verification = new EmailVerification();
        verification.setUser(user);
        verification.setOtp(CredentialDigests.hmacSha256(otp, otpHmacSecret));
        verification.setExpiresAt(now().plusMinutes(authProperties.getOtpExpiryMinutes()));
        verification.setVerified(false);
        verification.setFailedAttempts(0);
        emailVerificationRepository.save(verification);

        afterCommit(() -> sendMailSafely(() -> emailService.sendOtpEmail(
                user.getEmail(),
                user.getFullName(),
                otp)));
    }

    private String persistRefreshToken(User user, RefreshToken replaced) {
        if (replaced == null) {
            capActiveRefreshTokens(user);
        }
        String rawToken = UUID.randomUUID().toString();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(CredentialDigests.sha256(rawToken));
        refreshToken.setExpiresAt(now().plusDays(authProperties.getRefreshExpiryDays()));
        refreshToken.setRevoked(false);
        refreshToken.setReplacedByToken(null);
        refreshTokenRepository.save(refreshToken);
        if (replaced != null) {
            replaced.setReplacedByToken(refreshToken.getToken());
        }
        return rawToken;
    }

    private void capActiveRefreshTokens(User user) {
        int max = authProperties.getMaxActiveRefreshTokens();
        if (max <= 0) {
            return;
        }
        List<RefreshToken> active = refreshTokenRepository.findAllByUserIdAndRevokedFalseOrderByCreatedAtAsc(user.getId());
        int overflow = active.size() - max + 1;
        if (overflow <= 0) {
            return;
        }
        for (int i = 0; i < overflow; i++) {
            active.get(i).setRevoked(true);
        }
        refreshTokenRepository.saveAll(active.subList(0, overflow));
    }

    private AuthResponse buildAuthResponse(User user) {
        String refreshToken = persistRefreshToken(user, null);
        String accessToken = jwtService.generateToken(user);
        return new AuthResponse(accessToken, "Bearer", refreshToken);
    }

    private void revokeAllRefreshTokens(User user) {
        var activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId());
        for (RefreshToken token : activeTokens) {
            token.setRevoked(true);
        }
        refreshTokenRepository.saveAll(activeTokens);
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {
        User user = currentUserService.getCurrentUser();
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(
                        CredentialDigests.sha256(request.getRefreshToken()))
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token."));

        if (!refreshToken.getUser().getId().equals(user.getId())) {
            throw new InvalidRefreshTokenException("Invalid refresh token.");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    @Override
    @Transactional
    public void logoutAllDevices() {
        User user = currentUserService.getCurrentUser();
        bumpTokenVersion(user);
        userRepository.save(user);
        revokeAllRefreshTokens(user);
    }

    private void bumpTokenVersion(User user) {
        int current = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        user.setTokenVersion(current + 1);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    private void sendMailSafely(Runnable send) {
        try {
            send.run();
        } catch (EmailDeliveryException ex) {
            log.error("Email delivery failed after the account change was saved. Use resend-otp or forgot-password.");
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}

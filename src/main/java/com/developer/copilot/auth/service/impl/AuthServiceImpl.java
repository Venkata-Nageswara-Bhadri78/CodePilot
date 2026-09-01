package com.developer.copilot.auth.service.impl;

import com.developer.copilot.auth.repository.EmailVerificationRepository;
import com.developer.copilot.auth.repository.PasswordResetTokenRepository;
import com.developer.copilot.auth.repository.RefreshTokenRepository;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
import com.developer.copilot.auth.exception.InvalidCredentialsException;
import com.developer.copilot.auth.exception.ResourceAlreadyExistsException;
import com.developer.copilot.auth.jwt.JwtService;
import com.developer.copilot.auth.mapper.AuthMapper;
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

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{
    
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

    @Override
    @Transactional
    public void register(RegisterRequest registerRequest) {
        String username = normalizeUsername(registerRequest.getUsername());
        String email = normalizeEmail(registerRequest.getEmail());
        String fullName = registerRequest.getFullName() == null ? null : registerRequest.getFullName().trim();

        if (userRepository.existsByUsername(username)) {
            throw new ResourceAlreadyExistsException("Username already exists.");
        }
        
        if (userRepository.existsByEmail(email)) {
            throw new ResourceAlreadyExistsException("Email already exists.");
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

        userRepository.save(user);

        String otp = OtpGenerator.generateOtp();
        emailVerificationRepository.deleteByUserId(user.getId());
        EmailVerification verification = new EmailVerification();
        verification.setUser(user);
        verification.setOtp(CredentialDigests.sha256(otp));
        verification.setExpiresAt(now().plusMinutes(authProperties.getOtpExpiryMinutes()));
        verification.setVerified(false);
        verification.setFailedAttempts(0);

        emailVerificationRepository.save(verification);
        afterCommit(() -> emailService.sendOtpEmail(
            user.getEmail(),
            user.getFullName(),
            otp
        ));
    }

    @Override
    public AuthResponse login(LoginRequest request){
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid Email or Password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password.");
        }
        
        if (!user.getEmailVerified()) {
            throw new InvalidCredentialsException("Please verify your email before logging in.");
        }
        
        if (!user.getEnabled()) {
            throw new InvalidCredentialsException("Account is disabled.");
        }

        return buildAuthResponse(user);
    }

    @Override
    public UserResponse me() {
        return authMapper.toUserResponse(currentUser());
    }

    @Override
    @Transactional
    public void verifyOtp(VerifyOtpRequest request) {

        EmailVerification verification =
        emailVerificationRepository
                .findTopByUserEmailOrderByCreatedAtDesc(normalizeEmail(request.getEmail()))
                .orElseThrow(() ->
                        new InvalidOtpException("OTP not found."));

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

        if (!CredentialDigests.matches(request.getOtp(), verification.getOtp())) {
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

        userRepository.findByEmail(normalizeEmail(request.getEmail())).ifPresent(user -> {
            if (Boolean.TRUE.equals(user.getEmailVerified())) {
                return;
            }

            String otp = OtpGenerator.generateOtp();
            emailVerificationRepository.deleteByUserId(user.getId());
            EmailVerification verification = new EmailVerification();

            verification.setUser(user);
            verification.setOtp(CredentialDigests.sha256(otp));
            verification.setExpiresAt(now().plusMinutes(authProperties.getOtpExpiryMinutes()));
            verification.setVerified(false);
            verification.setFailedAttempts(0);

            emailVerificationRepository.save(verification);

            afterCommit(() -> emailService.sendOtpEmail(
                    user.getEmail(),
                    user.getFullName(),
                    otp));
        });
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(normalizeEmail(request.getEmail())).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUserIdAndUsedFalse(user.getId());

            String rawToken = UUID.randomUUID().toString();
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(CredentialDigests.sha256(rawToken));
            resetToken.setExpiresAt(now().plusMinutes(authProperties.getResetExpiryMinutes()));
            resetToken.setUsed(false);
            resetToken.setUsedAt(null);

            passwordResetTokenRepository.save(resetToken);

            afterCommit(() -> emailService.sendPasswordResetEmail(
                    user.getEmail(),
                    user.getFullName(),
                    rawToken
            ));
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
                .orElseThrow(() ->
                        new InvalidRefreshTokenException("Invalid refresh token."));

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

    private String persistRefreshToken(User user, RefreshToken replaced) {
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
        User user = currentUser();
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
        User user = currentUser();
        bumpTokenVersion(user);
        userRepository.save(user);
        revokeAllRefreshTokens(user);
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new InvalidCredentialsException("User not found.");
        }
        return userDetails.getUser();
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
        return username == null ? null : username.trim();
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

package com.developer.copilot.auth.service.impl;

import com.developer.copilot.auth.repository.EmailVerificationRepository;
import com.developer.copilot.auth.repository.PasswordResetTokenRepository;
import com.developer.copilot.auth.repository.RefreshTokenRepository;
import com.developer.copilot.auth.repository.UserRepository;
import com.developer.copilot.auth.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.developer.copilot.auth.service.AuthService;
import com.developer.copilot.auth.service.EmailService;
import com.developer.copilot.auth.util.OtpGenerator;
import com.developer.copilot.auth.exception.InvalidOtpException;
import com.developer.copilot.auth.exception.InvalidPasswordResetTokenException;
import com.developer.copilot.auth.exception.InvalidRefreshTokenException;
import com.developer.copilot.auth.exception.OtpExpiredException;
import com.developer.copilot.auth.exception.PasswordResetTokenExpiredException;
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

    @Override
    @Transactional
    public void register(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new ResourceAlreadyExistsException("Username already exists.");
        }
        
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new ResourceAlreadyExistsException("Email already exists.");
        }
        
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setFullName(registerRequest.getFullName());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        user.setRole(Role.USER);
        user.setEnabled(false);
        user.setEmailVerified(false);

        userRepository.save(user);

        emailVerificationRepository.deleteByUserId(user.getId());
        EmailVerification verification = new EmailVerification();
        verification.setUser(user);
        verification.setOtp(OtpGenerator.generateOtp());
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        verification.setVerified(false);

        emailVerificationRepository.save(verification);
        emailService.sendOtpEmail(
            user.getEmail(),
            user.getFullName(),
            verification.getOtp()
        );
    }

    @Override
    public AuthResponse login(LoginRequest request){
        User user = userRepository.findByEmail(request.getEmail())
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

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        CustomUserDetails userDetails =
                (CustomUserDetails) authentication.getPrincipal();

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow();

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    @Override
    public void verifyOtp(VerifyOtpRequest request) {

        EmailVerification verification =
        emailVerificationRepository
                .findTopByUserEmailOrderByCreatedAtDesc(request.getEmail())
                .orElseThrow(() ->
                        new InvalidOtpException("OTP not found."));

        User user = verification.getUser();

        if (Boolean.TRUE.equals(verification.getVerified())) {
            throw new InvalidOtpException("OTP already used.");
        }

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new OtpExpiredException("OTP has expired.");
        }

        if (!verification.getOtp().equals(request.getOtp())) {
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

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new InvalidOtpException("Invalid email."));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new InvalidOtpException("Email already verified.");
        }

        emailVerificationRepository.deleteByUserId(user.getId());
        EmailVerification verification = new EmailVerification();

        verification.setUser(user);
        verification.setOtp(OtpGenerator.generateOtp());
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        verification.setVerified(false);

        emailVerificationRepository.save(verification);

        emailService.sendOtpEmail(
                user.getEmail(),
                user.getFullName(),
                verification.getOtp());
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUserIdAndUsedFalse(user.getId());

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(UUID.randomUUID().toString());
            resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(15));
            resetToken.setUsed(false);
            resetToken.setUsedAt(null);

            passwordResetTokenRepository.save(resetToken);

            emailService.sendPasswordResetEmail(
                    user.getEmail(),
                    user.getFullName(),
                    resetToken.getToken()
            );
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new InvalidPasswordResetTokenException("Invalid password reset token."));

        if (Boolean.TRUE.equals(resetToken.getUsed())) {
            throw new InvalidPasswordResetTokenException("Password reset token is invalid or already used.");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PasswordResetTokenExpiredException("Password reset token has expired.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        revokeAllRefreshTokens(user);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() ->
                        new InvalidRefreshTokenException("Invalid refresh token."));

        if (Boolean.TRUE.equals(storedToken.getRevoked())) {
            throw new RefreshTokenRevokedException("Refresh token has been revoked.");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RefreshTokenExpiredException("Refresh token has expired.");
        }

        User user = storedToken.getUser();

        storedToken.setRevoked(true);

        RefreshToken newRefreshToken = createRefreshToken(user);
        storedToken.setReplacedByToken(newRefreshToken.getToken());

        refreshTokenRepository.save(storedToken);

        String newAccessToken = jwtService.generateToken(user);
        return new AuthResponse(newAccessToken, "Bearer", newRefreshToken.getToken());
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(30));
        refreshToken.setRevoked(false);
        refreshToken.setReplacedByToken(null);
        return refreshTokenRepository.save(refreshToken);
    }
    
    private AuthResponse buildAuthResponse(User user) {
        RefreshToken refreshToken = createRefreshToken(user);
        String accessToken = jwtService.generateToken(user);
        return new AuthResponse(accessToken, "Bearer", refreshToken.getToken());
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
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token."));

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    @Override
    @Transactional
    public void logoutAllDevices() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("User not found."));

        revokeAllRefreshTokens(user);
    }
}

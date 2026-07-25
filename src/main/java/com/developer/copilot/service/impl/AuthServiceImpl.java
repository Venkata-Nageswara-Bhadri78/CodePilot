package com.developer.copilot.service.impl;

import com.developer.copilot.repository.EmailVerificationRepository;
import com.developer.copilot.repository.UserRepository;
import com.developer.copilot.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.developer.copilot.dto.auth.AuthResponse;
import com.developer.copilot.dto.auth.LoginRequest;
import com.developer.copilot.dto.auth.RegisterRequest;
import com.developer.copilot.dto.auth.UserResponse;
import com.developer.copilot.entity.EmailVerification;
import com.developer.copilot.entity.User;
import com.developer.copilot.enums.Role;
import com.developer.copilot.exception.InvalidCredentialsException;
import com.developer.copilot.exception.ResourceAlreadyExistsException;
import com.developer.copilot.jwt.JwtService;
import com.developer.copilot.service.AuthService;
import com.developer.copilot.service.EmailService;
import com.developer.copilot.util.OtpGenerator;
import com.developer.copilot.dto.auth.ResendOtpRequest;
import com.developer.copilot.dto.auth.VerifyOtpRequest;
import com.developer.copilot.exception.InvalidOtpException;
import com.developer.copilot.exception.OtpExpiredException;



@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{
    
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final JwtService jwtService;
    private final EmailService emailService;

    @Override
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

        return new AuthResponse(
            jwtService.generateToken(user),
            "Bearer"
        );
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
}

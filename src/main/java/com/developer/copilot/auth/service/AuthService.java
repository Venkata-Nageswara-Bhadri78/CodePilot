package com.developer.copilot.auth.service;

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


public interface AuthService {
    void register(RegisterRequest registerRequest);
    AuthResponse login(LoginRequest request);
    UserResponse me();
    void verifyOtp(VerifyOtpRequest request);
    void resendOtp(ResendOtpRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(LogoutRequest request);
    void logoutAllDevices();
}

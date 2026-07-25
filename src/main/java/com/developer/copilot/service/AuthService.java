package com.developer.copilot.service;

import com.developer.copilot.dto.auth.AuthResponse;
import com.developer.copilot.dto.auth.LoginRequest;
import com.developer.copilot.dto.auth.RegisterRequest;
import com.developer.copilot.dto.auth.ResendOtpRequest;
import com.developer.copilot.dto.auth.UserResponse;
import com.developer.copilot.dto.auth.VerifyOtpRequest;

public interface AuthService {
    void register(RegisterRequest registerRequest);
    AuthResponse login(LoginRequest request);
    UserResponse me();
    void verifyOtp(VerifyOtpRequest request);
    void resendOtp(ResendOtpRequest request);
}

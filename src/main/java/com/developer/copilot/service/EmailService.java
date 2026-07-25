package com.developer.copilot.service;

public interface EmailService {
    void sendOtpEmail(String recipientEmail, String recipientName, String otp);
}

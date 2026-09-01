package com.developer.copilot.auth.service.impl;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.developer.copilot.auth.config.EmailProperties;
import com.developer.copilot.auth.exception.EmailDeliveryException;
import com.developer.copilot.auth.service.EmailService;
import java.util.Map;

import com.developer.copilot.auth.util.EmailTemplateService;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;
    private final EmailTemplateService emailTemplateService;

    @PostConstruct
    void validateMailProperties() {
        if (emailProperties.getFrom() == null || emailProperties.getFrom().isBlank()
                || emailProperties.getSenderName() == null || emailProperties.getSenderName().isBlank()) {
            throw new IllegalStateException("app.mail.from and app.mail.sender-name must be configured.");
        }
    }

    @Override
    public void sendOtpEmail(String recipientEmail, String recipientName, String otp) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(emailProperties.getFrom(), emailProperties.getSenderName());

            helper.setTo(recipientEmail);

            helper.setSubject("Verify your Email - AI Copilot");

            String html = emailTemplateService.process(
                "otp-email",
                Map.of(
                        "name", recipientName,
                        "otp", otp));
        
            helper.setText(html, true);

            mailSender.send(message);

        } catch (Exception ex) {
            log.error("Failed to send verification email");
            throw new EmailDeliveryException("Unable to send verification email.", ex);
        }
    }

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String recipientName, String resetToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(emailProperties.getFrom(), emailProperties.getSenderName());
            helper.setTo(recipientEmail);
            helper.setSubject("Reset your Password - AI Copilot");

            String html = emailTemplateService.process(
                    "password-reset",
                    Map.of(
                            "name", recipientName,
                            "token", resetToken,
                            "expiryMinutes", 15
                    )
            );

            helper.setText(html, true);
            mailSender.send(message);

        } catch (Exception ex) {
            log.error("Failed to send password reset email");
            throw new EmailDeliveryException("Unable to send password reset email.", ex);
        }
    }
}

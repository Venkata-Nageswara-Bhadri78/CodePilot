package com.developer.copilot.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.developer.copilot.config.EmailProperties;
import com.developer.copilot.service.EmailService;
import java.util.Map;

import com.developer.copilot.util.EmailTemplateService;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;
    private final EmailTemplateService emailTemplateService;

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
            ex.printStackTrace();
            throw new RuntimeException("Unable to send verification email.", ex);
        }
    }
}